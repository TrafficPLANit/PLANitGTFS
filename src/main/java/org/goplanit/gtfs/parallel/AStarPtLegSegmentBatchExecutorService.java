package org.goplanit.gtfs.parallel;

import org.goplanit.algorithms.shortest.ShortestPathAStar;
import org.goplanit.algorithms.shortest.ShortestPathResult;
import org.goplanit.network.layer.service.ServiceLegSegmentImpl;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.graph.directed.DirectedVertex;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.misc.IterableUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.ServiceNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.network.layer.service.ServiceLegSegment;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.path.SimpleDirectedPath;
import org.goplanit.utils.path.SimpleDirectedPathFactoryImpl;
import org.goplanit.utils.path.SimpleDirectedPathImpl;
import org.goplanit.utils.zoning.TransferConnectoid;
import org.goplanit.utils.zoning.TransferZone;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.goplanit.utils.zoning.ZoneConnectoidType.PT_VEHICLE_STOP;

/**
 * Executes A* star shortest path search in threaded batch mode between Service Network Leg Segments in the desired
 * number of threads and batch size. Populated physical parent leg segments in each service leg segment as a result
 */
public final class AStarPtLegSegmentBatchExecutorService {

  private static final Logger LOGGER = Logger.getLogger(AStarPtLegSegmentBatchExecutorService.class.getCanonicalName());

  /* default batch size set to 1024 */
  private static final int DEFAULT_BATCH_SIZE = 1024;

  /** default threads is one less than available processors or 1 if that is 0*/
  private static final int DEFAULT_NUM_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

  /**
   * Inputs to a single A* shortest path search for a given service leg segment which will be executed in batches
   * per thread
   */
  private static final class AStarLegSegmentCallInput {
    final ServiceNetworkLayer layer;
    final ServiceLegSegmentImpl seg;

    AStarLegSegmentCallInput(ServiceNetworkLayer layer, ServiceLegSegmentImpl seg) {
      this.layer = layer;
      this.seg = seg;
    }

  }

  /**
   * Output is only used for logging success rates, each leg segment may generate at most - and is expected to - a
   * single path
   */
  private static final class SingleBatchResult {
    final long processedLegSegments;
    final long validPathsFound;

    SingleBatchResult(long processedLegSegments, long validPathsFound) {
      this.processedLegSegments = processedLegSegments;
      this.validPathsFound = validPathsFound;
    }
  }

  /**
   * contains data shared between threads
   */
  private final AStarBatchExecutionData sharedData;

  /**
   * Construct batches and submit them while tracking total via mutable counter
   *
   * @param batchSize to use
   * @param cs service to schedule
   * @param mutableSubmittedCounter counter to track
   */
  private void constructAndSubmitBatches(
          int batchSize, CompletionService<SingleBatchResult> cs, int[] mutableSubmittedCounter) {
    final List<AStarLegSegmentCallInput> singleBatch = new ArrayList<>(batchSize);

    // ---- produce batches ----
    for (ServiceNetworkLayer layer : sharedData.getServiceNetwork().getTransportLayers()) {
      for (var leg : layer.getLegs()) {

        // leg.forEachSegment(...) is callback-based, so we fill & flush batches from inside
        leg.forEachSegment(seg0 -> {
          ServiceLegSegmentImpl seg = (ServiceLegSegmentImpl) seg0;
          singleBatch.add(new AStarLegSegmentCallInput(layer, seg));

          if (singleBatch.size() >= batchSize) {
            final List<AStarLegSegmentCallInput> toSubmit = new ArrayList<>(singleBatch);
            singleBatch.clear();

            cs.submit(() -> processBatch(toSubmit));  // Callable<BatchResult>
            mutableSubmittedCounter[0]++;
          }
        });
      }
    }

    // leftover last batch
    if (!singleBatch.isEmpty()) {
      final List<AStarLegSegmentCallInput> toSubmit = new ArrayList<>(singleBatch);
      singleBatch.clear();
      cs.submit(() -> processBatch(toSubmit));
      mutableSubmittedCounter[0]++;
    }
  }

  /**
   * Assumed batches have been scheduled. Here we await their completion and process results
   *
   * @param cs service used
   * @param numBatches batches scheduled to await
   * @throws InterruptedException if error
   */
  private void awaitAndConsumeBatchResults(CompletionService<SingleBatchResult> cs, int numBatches)
          throws InterruptedException, ExecutionException {
    long totalProcessed = 0;
    long totalValid = 0;
    long nextLogAt = 1_000; // doubling threshold (1k,2k,4k,...)

    // ---- consume results as they complete ----
    for (int i = 0; i < numBatches; i++) {
      // blocks until one batch completes [2](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/ExecutorCompletionService.html)
      SingleBatchResult r = cs.take().get();
      totalProcessed += r.processedLegSegments;
      totalValid += r.validPathsFound;

      if (totalProcessed >= nextLogAt) {
        double pct = totalProcessed == 0 ? 0.0 : (totalValid * 100.0 / totalProcessed);
        LOGGER.info(String.format(
                "Mapped %d service leg segments to network (%.2f%% successfully)",
                totalProcessed, pct));
        nextLogAt *= 2;
      }
    }
  }

  /**
   * Process a batch within a thread
   * @param batch the batch of inputs to process
   * @return the batch result to provide
   */
  private SingleBatchResult processBatch(List<AStarLegSegmentCallInput> batch) {
    long processed = 0;
    long valid = 0;

    for (AStarLegSegmentCallInput ls : batch) {
      mapServiceLegSegmentToPhysicalNetwork(ls.layer, ls.seg);
      processed++;
      if (ls.seg.hasPhysicalParentSegments()) {
        valid++;
      }
    }
    return new SingleBatchResult(processed, valid);
  }

  /**
   * Get the connectoids for given transfer zone, grouped by unique access nodes (as multiple access nodes across
   * more than one connectoid might exist)
   *
   * @param gtfsStopId provided for logging purposes
   * @param transferZone       to use
   * @return connectoids found, grouped by access node
   */
  private Map<DirectedVertex, List<TransferConnectoid>> findTransferZoneConnectoidsGroupByAccessNode(
          String gtfsStopId, TransferZone transferZone, ServiceNode gtfsStopServiceNode) {
    var transferZoneConnectoids = sharedData.getConnectoidsByAccessZone(transferZone);

    /* it is possible multiple connectoids exist, e.g., train platforms with access on both sides in either direction,
    therefore we group by access node */
    var resultByAccessNode = transferZoneConnectoids.stream().collect(
            Collectors.groupingBy(TransferConnectoid::getReferenceVertex));

    /* When GTFS stop has been linked to a service node which in turn has already been mapped to a physical node,
     * then we must limit the connectoids we consider to access nodes matching the physical node that is related to
     * this service node */
    if(gtfsStopServiceNode.hasPhysicalParentNodes()){
      resultByAccessNode.entrySet().removeIf( e -> !gtfsStopServiceNode.isMappedToPhysicalParentNode(
              (Node)e.getKey()));
    }

    if(resultByAccessNode.isEmpty() && gtfsStopServiceNode.hasPhysicalParentNodes()){
      LOGGER.severe(String.format("Unable to find available transfer zone access nodes for leg segment, likely " +
              "GTFS stop %s mapped to incorrect physical access node upon earlier path search", gtfsStopId));
    }
    return resultByAccessNode;
  }

  /**
   * Perform the integration for a given service layer's service leg's leg segment,
   * where we identify a path on the physical network between the service nodes. Note that we create physical paths
   * for the pt mode on the layer/segment regardless if an actual trip takes place between the leg segment stops.
   * <p>
   *   Also note that if we find multiple paths between the two service nodes as a result of the service nodes
   *   supporting multiple modes requiring different physical paths, we create additional legs and leg segments
   *   between those two service nodes!!
   * </p>
   *
   * @param layer the segment resides in
   * @param legSegment between two service nodes that will be populated with physical link segments (references)
   */
  private void mapServiceLegSegmentToPhysicalNetwork(
          ServiceNetworkLayer layer, ServiceLegSegmentImpl legSegment){

    Mode expectedMode = sharedData.getExpectedModeForServiceLeg(legSegment.getParent());
    var chosenPath = findMostLikelyPathBetweenGtfsStopServiceNodes(layer, legSegment, expectedMode);
    if(chosenPath != null) {
      /* now attach the link segments to the service leg segment based on the found path */
      legSegment.setPhysicalParentSegments(IterableUtils.toTypeCastList(chosenPath));
    }
  }

  /**
   * Given a network layer and two GTFS stop's transfer zones, find the most likely path between them taking the
   * mode and shortest distance into account
   *
   * @param layer to use for the physical network
   * @param serviceLegSegment to find physical path for
   * @param mode to find path for as layer might support multiple modes and available connectoids might as well
   * @return found most likely physical path (if any, can be null)
   */
  private SimpleDirectedPath findMostLikelyPathBetweenGtfsStopServiceNodes(
          ServiceNetworkLayer layer, ServiceLegSegment serviceLegSegment, Mode mode) {

    var serviceNodeToStopIdMapping = sharedData.getServiceNodeToGtfsStopIdMapping();
    var stopIdToTransferZoneMapping = sharedData.getGtfsStopIdToTransferZoneMapping();

    var gtfsStopIdUpstream = serviceNodeToStopIdMapping.apply(serviceLegSegment.getUpstreamServiceNode());
    TransferZone transferZoneUpstream = stopIdToTransferZoneMapping.apply(gtfsStopIdUpstream);

    var gtfsStopIdDownstream = serviceNodeToStopIdMapping.apply(serviceLegSegment.getDownstreamServiceNode());
    TransferZone transferZoneDownstream = stopIdToTransferZoneMapping.apply(gtfsStopIdDownstream);
    if(transferZoneUpstream==null || transferZoneDownstream == null){
      /* likely no mapping found for stops due to physical network not being close enough, i.e.,
       * routes/legs/nodes fall outside bounding box of physical network we are mapping to */
      return null;
    }

    /* link service node to transfer zone access nodes (which are physical nodes) */
    var upstreamConnectoidsByAccessNode = findTransferZoneConnectoidsGroupByAccessNode(
            gtfsStopIdUpstream, transferZoneUpstream, serviceLegSegment.getUpstreamServiceNode());
    var downstreamConnectoidsByAccessNode = findTransferZoneConnectoidsGroupByAccessNode(
            gtfsStopIdDownstream, transferZoneDownstream, serviceLegSegment.getDownstreamServiceNode());
    if(upstreamConnectoidsByAccessNode.isEmpty() || downstreamConnectoidsByAccessNode.isEmpty()){
      return null;
    }

    SimpleDirectedPath chosenPath = null;
    if (!layer.supports(mode)) {
      LOGGER.severe(String.format("Service layer does not seem to support the mode (%s), the service leg is " +
              "attributed to, this shouldn't happen", mode.getName()));
      return null;
    }

    // thread safe access
    var shortestPathAlgo = sharedData.getShortestPathAlgoForCurrentThread(mode);

    /* prune to connectoids that are mode compatible for type STOP */
    boolean defaultModeAllowedIfZoneTypeAbsent = false;
    upstreamConnectoidsByAccessNode.values().forEach(
            cList -> cList.removeIf(c ->
                !c.isModeAllowed(transferZoneUpstream, PT_VEHICLE_STOP, mode, defaultModeAllowedIfZoneTypeAbsent)));
    downstreamConnectoidsByAccessNode.values().forEach(
            cList -> cList.removeIf(c ->
                !c.isModeAllowed(transferZoneDownstream, PT_VEHICLE_STOP, mode, defaultModeAllowedIfZoneTypeAbsent)));

    // proceed when both connectoids support the mode on any of its access nodes
    if (upstreamConnectoidsByAccessNode.values().stream().flatMap(Collection::stream).findFirst().isEmpty() &&
            downstreamConnectoidsByAccessNode.values().stream().flatMap(Collection::stream).findFirst().isEmpty()) {
      LOGGER.severe(String.format("Service leg segment connecting GTFS stop pair [%s (%s), %s (%s)] not mode " +
                      "compatible [mode (%s)] with PLANit mapped stops (connectoids), this shouldn't happen",
              gtfsStopIdUpstream, transferZoneUpstream.getName(), gtfsStopIdDownstream,
              transferZoneDownstream.getName(), mode.getName()));
      return null;
    }

    final var finalDownstreamConnectoidsByAccessNode = downstreamConnectoidsByAccessNode;
    final var allLegSegmentPathOptions = new TreeSet<SimpleDirectedPath>(Comparator.comparing(Object::hashCode));

    // Do this ordered in case we have identical distance options for which we want to at least be consistent
    // between runs Lambda so "return" is a "continue"
    upstreamConnectoidsByAccessNode.entrySet().stream().sorted(
        Map.Entry.comparingByKey()).forEach( upstreamEntry  -> {
      if(upstreamEntry.getValue().isEmpty()){
        return;
      }
      finalDownstreamConnectoidsByAccessNode.entrySet().stream().sorted(
              Map.Entry.comparingByKey()).forEach(downstreamEntry  -> {
        if(downstreamEntry.getValue().isEmpty()){
          return;
        }

        // find eligible paths between upstream access node and downstream access node(s).
        Collection<SimpleDirectedPath> accessNodePathOptions =
                createShortestPathsBetweenAccessNodes(
                        mode,
                        upstreamEntry.getValue(),
                        transferZoneUpstream,
                        downstreamEntry.getValue(),
                        transferZoneDownstream,
                        shortestPathAlgo);
        allLegSegmentPathOptions.addAll(accessNodePathOptions);
      });
    });

    // when no options are found but connectoids support current mode, issue a warning
    if (allLegSegmentPathOptions.isEmpty()) {
      var upstreamLocation =
              transferZoneUpstream.hasGeometry() ? transferZoneUpstream.getGeometry() :
                      transferZoneUpstream.getCentroid().getPosition();
      var downstreamLocation =
              transferZoneDownstream.hasGeometry() ? transferZoneDownstream.getGeometry() :
                      transferZoneDownstream.getCentroid().getPosition();
      LOGGER.warning(String.format("No eligible physical path for valid service leg segment [mode (%s)] " +
                      "between GTFS stops [%s (%s, %s), %s (%s, %s)" +
                      "], verify if path (partly) exits parsed bounding area",
              mode.getName(),
              gtfsStopIdUpstream,
              transferZoneUpstream.getName(),
              upstreamLocation.toString(),
              gtfsStopIdDownstream,
              transferZoneDownstream.getName(),
              downstreamLocation.toString()));
      return null;
    }

    chosenPath = allLegSegmentPathOptions.iterator().next();
    if (allLegSegmentPathOptions.size() > 1) {
      //  We can have multiple paths still despite this being a call for a single leg segment. This is because it is
      //  possible that the related transfer zone of the service node may represent multiple stops (and service nodes).
      //  Therefore, we must make an educated guess how to link the leg segment (and service node) to the found which
      //  of the found paths if multiple exist. Once a choice has been made, we will then encounter another leg segment
      //  later on which will generate the same paths but now should be matched to the remaining (other) path. This
      //  likely ONLY happens for consecutive train stations with platforms having tracks on both sides, e.g., redfern
      //  and central, or in case platforms are stacked on top of each other (the latter case we could improve by
      //  enforcing layer information if present, but this is not done yet) RULE --> use rule of thumb where we
      //  use the shortest path (this will eliminate crossing paths most likely (switches), we then  might still
      //  choose the wrong platform/track but this is not a big issue.
      LOGGER.fine(String.format("Multiple paths possible between two GTFS stops (%s, %s) for mode %s, due to GTFS " +
                      "stop having multiple possible access points to physical network, e.g.," +
                      " train platform, choosing first",
              gtfsStopIdUpstream, gtfsStopIdDownstream, mode.getName()));
      chosenPath = allLegSegmentPathOptions.stream().min(
              Comparator.comparingDouble(SimpleDirectedPath::computeLengthKm)).get();
    }

    return chosenPath;
  }

  /**
   * Find shortest paths between access nodes taking access link segments into account
   * todo: add access type for each entry as it matters if we are dealing with pt vehicle access or traveller access for
   *   example. Otherwise we are computing too many paths.
   *
   * @param mode to consider
   * @param upstreamAccessNodeConnectoids origin connectoids
   * @param tzUpstream origin transfer zone
   * @param downstreamAccessNodeConnectoids destination connectoids
   * @param tzDownstream destination transfer zone
   * @param shortestPathAlgo algo to use
   * @return found paths
   */
  private Collection<SimpleDirectedPath> createShortestPathsBetweenAccessNodes(
          Mode mode,
          List<TransferConnectoid> upstreamAccessNodeConnectoids,
          TransferZone tzUpstream,
          List<TransferConnectoid> downstreamAccessNodeConnectoids,
          TransferZone tzDownstream,
          ShortestPathAStar shortestPathAlgo) {

    List<SimpleDirectedPath> createdPaths = new LinkedList<>();
    boolean defaultModeAllowedIfZoneTypeAbsent = false;
    for(var upstreamConnectoid : upstreamAccessNodeConnectoids) {
      if (!upstreamConnectoid.isModeAllowed(tzUpstream, PT_VEHICLE_STOP, mode, defaultModeAllowedIfZoneTypeAbsent)) {
        continue;
      }
      var upstreamAccessEntry =
          upstreamConnectoid.getAsDirectedAccessZoneEntry(tzUpstream, PT_VEHICLE_STOP);
      for(var upstreamAccessSegment : upstreamAccessEntry.getAccessLinkSegments()) {
        if (!((MacroscopicLinkSegment)upstreamAccessSegment).isModeAllowed(mode)) {
          continue;
        }
        for(var downstreamConnectoid : downstreamAccessNodeConnectoids) {
          if (!downstreamConnectoid.isModeAllowed(
              tzDownstream, PT_VEHICLE_STOP, mode, defaultModeAllowedIfZoneTypeAbsent)) {
            continue;
          }
          var downstreamAccessEntry =
              downstreamConnectoid.getAsDirectedAccessZoneEntry(tzDownstream, PT_VEHICLE_STOP);
          for (var downstreamAccessSegment : downstreamAccessEntry.getAccessLinkSegments()) {
            if (!((MacroscopicLinkSegment) downstreamAccessSegment).isModeAllowed(mode)) {
              continue;
            }

            /* find shortest path using the upstream access node/segment and downstream access node/segment
             combinations to ensure that we use both access link segments in the final path we then supplement
             the found path with the two access link segments which we know are mode compatible */
            try {

              /* ban direct u-turn around access link segments, unless it is a water/rail mode where this can be
              acceptable */
              boolean banInitialUTurn = !(mode.hasPhysicalFeatures() &&
                  mode.getPhysicalFeatures().getTrackType() != TrackModeType.ROAD);

              // todo if ever we support turn bans, then we must make the below more sophisticated
              Set<EdgeSegment> bannedLinkSegments = new HashSet<>();
              if(upstreamAccessSegment.getOppositeDirectionSegment() != null && banInitialUTurn){
                bannedLinkSegments.add(upstreamAccessSegment.getOppositeDirectionSegment());
              }
              if(downstreamAccessSegment.getOppositeDirectionSegment() != null){
                bannedLinkSegments.add(downstreamAccessSegment.getOppositeDirectionSegment());
              }

              /* execute shortest path */
              ShortestPathResult result = shortestPathAlgo.executeOneToOne(
                  upstreamConnectoid.getReferenceVertex(),
                  downstreamAccessSegment.getUpstreamVertex(),
                  bannedLinkSegments);
              var foundPath = (SimpleDirectedPathImpl) result.createPath(
                  new SimpleDirectedPathFactoryImpl(),
                  upstreamConnectoid.getReferenceVertex(),
                  downstreamAccessSegment.getUpstreamVertex());

              foundPath.append(downstreamAccessSegment);
              createdPaths.add(foundPath);
              //LOGGER.info(StreamSupport.stream(foundPath.spliterator(), false).map( e -> e.getParent().getExternalId()).collect(Collectors.joining(", ")));
            } catch (PlanItRunTimeException e) {
              /* when no path can be found this means we have a problem OR in case of multiple access nodes per
              transfer zone, e.g., station platform with tracks on either side it can still be fine. We therefore do
              not report a problem if no path between upstream access node and used downstream access node can be found */
            }
          } // downstr segm
        } // downstr connectoid
      } // upstr access segm
    } // upstr connectoid

    /* discard redundant paths, for example an access node with two connectoids having two access link segments:
        o-------->*<--------o
        can result in situation of having two paths generated:
        1. o------->* and
        2. o-------->*------->o
                      <------/
        the second path is created because we require access via upstream node of access link segment, then
        supplementing with the final segment causes a u-turn. This is currently accepted if there is no other way
        to reach the access node (to be revisited), but here it makes no sense as we already have
        a better option. Therefore, we filter such redundant options out and do not use the path.
     */
    var iter = createdPaths.iterator();
    while(iter.hasNext()){
      var currOption = iter.next();
      if(createdPaths.stream().anyMatch(o -> o!=currOption && currOption.containsSubPath(o.iterator()))){
        iter.remove();
      }
    }
    return createdPaths;
  }

  /**
   * Constructor
   * @param sharedData to use
   */
  private AStarPtLegSegmentBatchExecutorService(AStarBatchExecutionData sharedData){
    this.sharedData = sharedData;
  }

  /** factory method
   *
   * @param sharedData to use
   * @return created instance
   */
  public static AStarPtLegSegmentBatchExecutorService create(AStarBatchExecutionData sharedData){
    return new AStarPtLegSegmentBatchExecutorService(sharedData);
  }

  /**
   * Execute with default threads (total - 1) and batch size (1024)
   *
   * @throws InterruptedException if error
   * @throws ExecutionException if error
   */
  public void execute() throws InterruptedException, ExecutionException{
    execute(DEFAULT_NUM_THREADS, DEFAULT_BATCH_SIZE);
  }

  /**
   * Execute with given threads and batch size
   *
   * @param threadsToUse to use this many threads
   * @param batchSize this batch size
   * @throws InterruptedException if error
   * @throws ExecutionException if error
   */
  public void execute(int threadsToUse, int batchSize) throws InterruptedException, ExecutionException{

    final ExecutorService exec = Executors.newFixedThreadPool(threadsToUse);
    // queues completed tasks for take() [2](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/ExecutorCompletionService.html)
    final CompletionService<SingleBatchResult> cs = new ExecutorCompletionService<>(exec);

    final int[] submitted = new int[] { 0 }; // mutable holder for lambda usage
    try {

      // schedule and trigger
      constructAndSubmitBatches(batchSize, cs, submitted);
      // consume results when done
      awaitAndConsumeBatchResults(cs, submitted[0]);

    } finally {
      // JDK guidance: shutdown unused executors to reclaim resources [3](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html)
      exec.shutdown();
      // Optional: wait a bit, then shutdownNow if needed
      // exec.awaitTermination(...);
    }
  }

}
