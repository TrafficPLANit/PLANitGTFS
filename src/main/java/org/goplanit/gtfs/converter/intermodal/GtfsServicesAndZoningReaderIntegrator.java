package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.algorithms.shortest.ShortestPathAStar;
import org.goplanit.algorithms.shortest.ShortestPathResult;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.network.layer.service.ServiceLegSegmentImpl;
import org.goplanit.path.SimpleDirectedPathFactoryImpl;
import org.goplanit.path.SimpleDirectedPathImpl;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.misc.IterableUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.ServiceNetworkLayer;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.network.layer.service.ServiceLegSegment;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.path.SimpleDirectedPath;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.zoning.Zoning;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Integrates the service network and routed services (GTFS itinerary) with the physical road network and zoning (GTFS stop based transfer zones)
 *
 * @author markr
 */
public class GtfsServicesAndZoningReaderIntegrator {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesAndZoningReaderIntegrator.class.getCanonicalName());

  /** data tracking useful information for during integration itself*/
  private final GtfsServicesAndZoningIntegratorData data;

  private final Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping;

  private final Function<String, TransferZone> gtfsStopIdToTransferZoneMapping;

  /**
   * Initialise some local indices that are to be used
   */
  private void initialise(){
    data.initialise();
  }

  /**
   * Get the connectoids for given transfer zone, grouped by unique access nodes (as multiple access nodes across more than one
   * connectoid might exist)
   *
   * @param gtfsStopId provided for logging purposes
   * @param transferZone       to use
   * @return connectoids found, grouped by access node
   */
  private Map<Node, List<DirectedConnectoid>> findTransferZoneConnectoidsGroupByAccessNode(
      String gtfsStopId, TransferZone transferZone, ServiceNode gtfsStopServiceNode) {
    var transferZoneConnectoids = data.getConnectoidsByAccessZone(transferZone);
    /* it is possible multiple connectoids exist, e.g., train platforms with access on both sides in either direction, therefore we group by
     * access node */
    var resultByAccessNode = transferZoneConnectoids.stream().collect(Collectors.groupingBy(c -> c.getAccessNode()));

    /* When GTFS stop has been linked to a service node which in turn has already been mapped to a physical node, then we must limit the connectoids we consider to
     * access nodes matching the physical node that is related to this service node */
    if(gtfsStopServiceNode.hasPhysicalParentNodes()){
      resultByAccessNode.entrySet().removeIf( e -> !gtfsStopServiceNode.isMappedToPhysicalParentNode(e.getKey()));
    }

    if(resultByAccessNode.isEmpty() && gtfsStopServiceNode.hasPhysicalParentNodes()){
      LOGGER.severe(String.format("Unable to find available transfer zone access nodes for leg segment, likely GTFS stop %s mapped to incorrect physical access node upon earlier path search", gtfsStopId));
    }
    return resultByAccessNode;
  }

  /**
   * Given a network layer and two GTFS stop's transfer zones, find the most likely path between them taking the mode and shortest distance into account
   *
   * @param layer to use for the physical network
   * @param serviceLegSegment to find physical path for
   * @param mode to find path for as layer might support multiple modes and available connectoids might as well
   * @return found most likely physical path (if any, can be null)
   */
  private SimpleDirectedPath findMostLikelyPathBetweenGtfsStopServiceNodes(
      ServiceNetworkLayer layer, ServiceLegSegment serviceLegSegment, Mode mode) {

    var gtfsStopIdUpstream = serviceNodeToGtfsStopIdMapping.apply(serviceLegSegment.getUpstreamServiceNode());
    TransferZone transferZoneUpstream = gtfsStopIdToTransferZoneMapping.apply(gtfsStopIdUpstream);

    var gtfsStopIdDownstream = serviceNodeToGtfsStopIdMapping.apply(serviceLegSegment.getDownstreamServiceNode());
    TransferZone transferZoneDownstream = gtfsStopIdToTransferZoneMapping.apply(gtfsStopIdDownstream);
    if(transferZoneUpstream==null || transferZoneDownstream == null){
      /* likely no mapping found for stops due to physical network not being close enough, i.e., routes/legs/nodes fall outside bounding box of physical network we are mapping to */
      return null;
    }

    if(gtfsStopIdUpstream.equals("2000449") && gtfsStopIdDownstream.equals("2000453")){
      int bla = 4;
    }

    /* link service node to transfer zone access nodes (which are physical nodes) */
    var upstreamConnectoidsByAccessNode = findTransferZoneConnectoidsGroupByAccessNode(gtfsStopIdUpstream, transferZoneUpstream, serviceLegSegment.getUpstreamServiceNode());
    var downstreamConnectoidsByAccessNode = findTransferZoneConnectoidsGroupByAccessNode(gtfsStopIdDownstream, transferZoneDownstream, serviceLegSegment.getDownstreamServiceNode());
    if(upstreamConnectoidsByAccessNode.isEmpty() || downstreamConnectoidsByAccessNode.isEmpty()){
      return null;
    }

    SimpleDirectedPath chosenPath = null;
    if (!layer.supports(mode)) {
      LOGGER.severe(String.format("Service layer does not seem to support the mode (%s), the service leg is attributed to, this shouldn't happen", mode.getName()));
      return null;
    }
    var shortestPathAlgo = data.getShortestPathAlgoByMode(mode);

    /* prune to connectoids that are mode compatible */
    upstreamConnectoidsByAccessNode.values().forEach(cList -> cList.removeIf( c -> !c.isModeAllowed(transferZoneUpstream, mode)));
    downstreamConnectoidsByAccessNode.values().forEach(cList -> cList.removeIf( c -> !c.isModeAllowed(transferZoneDownstream, mode)));

    // proceed when both connectoids support the mode on any of its access nodes
    if (!upstreamConnectoidsByAccessNode.values().stream().flatMap(e -> e.stream()).findFirst().isPresent() &&
        !downstreamConnectoidsByAccessNode.values().stream().flatMap(e -> e.stream()).findFirst().isPresent()) {
      LOGGER.severe(String.format("Service leg segment connecting GTFS stop pair [%s (%s), %s (%s)] not mode compatible [mode (%s)] with PLANit mapped stops (connectoids), this shouldn't happen",
          gtfsStopIdUpstream, transferZoneUpstream.getName(), gtfsStopIdDownstream, transferZoneDownstream.getName(), mode.getName()));
      return null;
    }

    final var finalDownstreamConnectoidsByAccessNode = downstreamConnectoidsByAccessNode;
    final var allLegSegmentPathOptions = new TreeSet<SimpleDirectedPath>(Comparator.comparing(Object::hashCode));

    // Do this ordered in case we have identical distance options for which we want to at least be consistent between runs
    // Lambda so "return" is a "continue"
    upstreamConnectoidsByAccessNode.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach( upstreamEntry  -> {
      if(upstreamEntry.getValue().isEmpty()){
        return;
      }
      finalDownstreamConnectoidsByAccessNode.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach( downstreamEntry  -> {
        if(downstreamEntry.getValue().isEmpty()){
          return;
        }

        // find eligible paths between upstream access node and downstream access node(s).
        Set<SimpleDirectedPath> accessNodePathOptions =
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
      LOGGER.warning(String.format("Valid service leg segment [mode (%s)] connects GTFS stops [%s (%s), %s (%s)], but no eligible physical path found, verify expected path (partly) exits parsed bounding box",
          mode.getName(), gtfsStopIdUpstream, transferZoneUpstream.getName(), gtfsStopIdDownstream, transferZoneDownstream.getName()));
      return null;
    }

    chosenPath = allLegSegmentPathOptions.iterator().next();
    if (allLegSegmentPathOptions.size() > 1) {
      //  We can have multiple paths still despite this being a call for a single leg segment. This is because it is possible that the related transfer zone of the
      //  service node may represent multiple stops (and service nodes). Therefore, we must make an educated guess how to link the leg segment (and service node) to the found
      //  which of the found paths if multiple exist. Once a choice has been made, we will then encounter another leg segment later on which will generate the same paths but now
      //  should be matched to the remaining (other) path. This likely ONLY happens for consecutive train stations with platforms having tracks on both sides, e.g., redfern and central, or
      // in case platforms are stacked on top of each other (the latter case we could improve by enforcing layer information if present, but this is not done yet)
      //  RULE --> use rule of thumb where we use the shortest path (this will eliminate crossing paths most likely (switches), we then
      //  might still choose the wrong platform/track but this is not a big issue.
      LOGGER.fine(String.format("Multiple paths possible between two GTFS stops (%s, %s) for mode %s, due to GTFS stop having multiple possible access points to physical network, e.g., train platform, choosing first", gtfsStopIdUpstream, gtfsStopIdDownstream, mode.getName()));
      chosenPath = allLegSegmentPathOptions.stream().min(Comparator.comparingDouble(p -> p.computeLengthKm())).get();
    }

    // print all subsequent (OSM) node external ids of each chosen path for visualisation/error checking purposes
    //LOGGER.info(mode.getName() + " " + chosenPath.iterator().next().getUpstreamVertex().getExternalId() + ","+ StreamSupport.stream(chosenPath.spliterator(), false).map(es -> es.getDownstreamVertex().getExternalId()).collect(Collectors.joining(", ")));
    return chosenPath;
  }

  private Set<SimpleDirectedPath> createShortestPathsBetweenAccessNodes(
          Mode mode,
          List<DirectedConnectoid> upstreamAccessNodeConnectoids,
          TransferZone transferZoneUpstream,
          List<DirectedConnectoid> downstreamAccessNodeConnectoids,
          TransferZone transferZoneDownstream, ShortestPathAStar shortestPathAlgo) {

    Set<SimpleDirectedPath> createdPaths = new HashSet<>();
    for(var upstreamConnectoid : upstreamAccessNodeConnectoids) {
      if (!(upstreamConnectoid.isModeAllowed(transferZoneUpstream, mode) && upstreamConnectoid.getAccessLinkSegment().isModeAllowed(mode))) {
        continue;
      }
      for(var downstreamConnectoid : downstreamAccessNodeConnectoids) {
        if (!(downstreamConnectoid.isModeAllowed(transferZoneDownstream, mode) && downstreamConnectoid.getAccessLinkSegment().isModeAllowed(mode))) {
          continue;
        }

        /* find shortest path using the upstream access node and downstream access link segment upstream node to ensure that we use both access link segments in the final path
           we then supplement the found path with the two access link segments which we know are mode compatible */
        try {

          /* ban direct u-turn around access link segments, unless it is a water/rail mode where this can be acceptable */
          boolean banInitialUTurn = !(mode.hasPhysicalFeatures() && mode.getPhysicalFeatures().getTrackType() != TrackModeType.ROAD);

          // todo if ever we support turn bans, then we must make the below more sophisticated
          Set<EdgeSegment> bannedLinkSegments = new HashSet<>();
          if(upstreamConnectoid.getAccessLinkSegment().getOppositeDirectionSegment() != null && banInitialUTurn){
            bannedLinkSegments.add(upstreamConnectoid.getAccessLinkSegment().getOppositeDirectionSegment());
          }
          if( downstreamConnectoid.getAccessLinkSegment().getOppositeDirectionSegment() != null){
            bannedLinkSegments.add( downstreamConnectoid.getAccessLinkSegment().getOppositeDirectionSegment());
          }

          /* execute shortest path */
          ShortestPathResult result = shortestPathAlgo.executeOneToOne(
              upstreamConnectoid.getAccessNode(), downstreamConnectoid.getAccessLinkSegment().getUpstreamNode(), bannedLinkSegments);
          var foundPath = (SimpleDirectedPathImpl) result.createPath(new SimpleDirectedPathFactoryImpl(), upstreamConnectoid.getAccessNode(), downstreamConnectoid.getAccessLinkSegment().getUpstreamNode());

          foundPath.append(downstreamConnectoid.getAccessLinkSegment());
          createdPaths.add(foundPath);
          //LOGGER.info(StreamSupport.stream(foundPath.spliterator(), false).map( e -> e.getParent().getExternalId()).collect(Collectors.joining(", ")));
        } catch (PlanItRunTimeException e) {
          /* when no path can be found this means we have a problem OR in case of multiple access nodes per transfer zone, e.g., station platform with tracks on either side
             it can still be fine. We therefore do not report a problem if no path between upstream access node and used downstream access node can be found */
        }
      }
    }
    /* discard redundant paths, for example an access node with two connectoids having two access link segments:
        o-------->*<--------o
        can result in situation of having two paths generated:
        1. o------->* and
        2. o-------->*------->o
                      <------/
        the second path is created because we require access via upstream node of access link segment, then supplementing with the final segment causes
        a u-turn. This is currently accepted if there is no other way to reach the access node (to be revisited), but here it makes no sense as we already have
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
   * Validate inputs to see if integration and creation of physical paths and relating them to the service network is supported
   */
  private void validateInputs() {
    PlanItRunTimeException.throwIfNull(this.serviceNodeToGtfsStopIdMapping, "serviceNodeToGtfsStopIdMapping is null");
    PlanItRunTimeException.throwIfNull(this.gtfsStopIdToTransferZoneMapping, "gtfsStopIdToTransferZoneMapping is null");
    PlanItRunTimeException.throwIfNull(data.getServiceNetwork(), "serviceNetwork is null");
    PlanItRunTimeException.throwIfNull(data.getSettings(), "GTFS Intermodal reader settings is null");
    PlanItRunTimeException.throwIfNull(data.getZoning(), "zoning is null");

    //todo: multiple layers should be possible to implement but at this point simply has not been done due to absence of a case where this is used
    PlanItRunTimeException.throwIf(data.getServiceNetwork().getParentNetwork().getTransportLayers().size()>1, "Currently GTFS converter only supports physical reference networks with a single layer");
    PlanItRunTimeException.throwIf(data.getServiceNetwork().getTransportLayers().size()>1, "Currently GTFS converter only supports service networks with a single layer");
  }

  /**
   * Perform the integration for a given service layer's service leg's leg segment,
   * where we identify a path on the physical network between the service nodes. Note that we create physical paths
   * for the pt mode on the layer/segment regardless if an actual trip takes place between the leg segment stops.
   * <p>
   *   Also note that if we find multiple paths between the two service nodes as a result of the service nodes supporting multiple
   *   modes requiring different physical paths, we create additional legs and leg segments between those two service nodes!!
   * </p>
   *
   * @param layer the segment resides in
   * @param legSegment between two service nodes that will be populated with physical link segments (references)
   */
  private void mapServiceLegSegmentToPhysicalNetwork(ServiceNetworkLayer layer, ServiceLegSegmentImpl legSegment){

    Mode expectedMode = data.getExpectedModeForServiceLeg(legSegment.getParent());
    var chosenPath = findMostLikelyPathBetweenGtfsStopServiceNodes(layer, legSegment, expectedMode);
    if(chosenPath != null) {
      /* now attach the link segments to the service leg segment based on the found path */
      legSegment.setPhysicalParentSegments(IterableUtils.toTypeCastList(chosenPath));
      return;
    }
  }

  /**
   * Constructor
   *
   * @param settings of the parent reader used
   * @param zoning to integrate
   * @param routedServices to integrate
   * @param serviceNetwork to integrate
   * @param serviceNodeToGtfsStopIdMapping mapping from PLANit service nodes to GTFS stop ids
   * @param gtfsStopIdToTransferZoneMapping mapping from GTFS stop id to PLANit transfer zone
   */
  public GtfsServicesAndZoningReaderIntegrator(
      GtfsIntermodalReaderSettings settings,
      Zoning zoning,
      ServiceNetwork serviceNetwork,
      RoutedServices routedServices,
      Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping,
      Function<String, TransferZone> gtfsStopIdToTransferZoneMapping) {

    this.serviceNodeToGtfsStopIdMapping = serviceNodeToGtfsStopIdMapping;
    this.gtfsStopIdToTransferZoneMapping = gtfsStopIdToTransferZoneMapping;

    this.data = new GtfsServicesAndZoningIntegratorData(serviceNetwork, routedServices, zoning, settings);

    validateInputs();
  }

  /**
   * Perform the integration where we identify paths between each of the used GTFS stop service nodes on the physical road network and update
   * the PLANit references in the service legs accordingly
   */
  public void execute() {
    initialise();

    /* process service leg segments - knowing that all leg segments are instances of ServiceLegSegmentImpl as this is how the GTFS converter has created them */
    data.getServiceNetwork().getTransportLayers().forEach(l -> l.getLegs().forEach(
        leg -> leg.forEachSegment( legSegment -> mapServiceLegSegmentToPhysicalNetwork(l, (ServiceLegSegmentImpl) legSegment))));
  }

  /**
   * Reset internal (temporary) state
   */
  public void reset(){
    data.reset();
  }
}
