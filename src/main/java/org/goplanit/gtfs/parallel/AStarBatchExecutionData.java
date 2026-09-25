package org.goplanit.gtfs.parallel;

import org.goplanit.algorithms.shortest.ShortestPathAStar;
import org.goplanit.cost.CostUtils;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.gtfs.converter.GtfsConverterModeMappingData;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseIssue;
import org.goplanit.gtfs.converter.intermodal.GtfsIntegrationProfiler;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.network.transport.TransportModelNetworkUtils;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.graph.directed.DirectedVertex;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.service.ServiceLeg;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.zoning.connectoid.TransferConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Data for integration where we create some local mappings based on the mode mapping from the settings among
 * other things.
 */
public class AStarBatchExecutionData {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(AStarBatchExecutionData.class.getCanonicalName());

  private final ServiceNetwork serviceNetwork;

  private final GtfsConverterModeMappingData modeMappingData;

  /** profiler tracking what became of each service leg segment and the GTFS entities behind it */
  private final GtfsIntegrationProfiler profiler;

  // local data during execution
  private Map<Zone, Set<TransferConnectoid>> connectoidsByAccessZone;

  private final Map<Mode, double[]> linkSegmentCostsByMode;

  private final Map<Mode, Double> heuristicMultiplierByMode;

  private final DirectedVertex[] idIndexedVerticesAllLayers;

  private final Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping;

  private final Function<String, TransferZone> gtfsStopIdToTransferZoneMapping;

  /**
   * what a GTFS stop was discarded for during the preceding stages, null where it survived them or was never seen.
   * <p>
   * An endpoint of a leg segment that cannot be reached is only worth reporting as a fault of this integration when
   * the stop behind it was one the run meant to keep. Where it was already let go for a reason of its own, that reason
   * is the explanation and this lookup supplies it
   * </p>
   */
  private final Function<String, GtfsParseIssue> gtfsStopIdToDiscardIssueMapping;

  /** shortest path algorithm used specific to each mode (and its link segment costs). We make it thread local so
   * it can be used in multi-threaded setup as it is not thread safe to use across threads */
  private ThreadLocal<Map<Mode, ShortestPathAStar>> shortestPathAlgoByModePerThread =
          ThreadLocal.withInitial(HashMap::new);

  /** track the expected mode to be used for a given service leg (before physical link segments have been
   * attached), based on the routed services that traverse it (which do have a mode) */
  private final Map<ServiceLeg, Mode> serviceLegToModeMapping;

  /**
   * Create and initialise shortest path algorithm with free flow costs for the
   * entire network for a given mode, so it can be reused when needed. NOT THREAD SAFE, NEEDS TO BE CREATED
   * WITHIN A THREAD LOCAL TO BE SAFE
   *
   * @param mode algo for this mode
   */
  private ShortestPathAStar createNewShortestPathAlgo(Mode mode) {
    /* prep shortest path algorithm (costs) per mode across network link segments for path searching, since costs
     * are fixed, we can do this beforehand and reuse */
    return new ShortestPathAStar(
            linkSegmentCostsByMode.get(mode),
            idIndexedVerticesAllLayers,
            serviceNetwork.getParentNetwork().getCoordinateReferenceSystem(),
            heuristicMultiplierByMode.get(mode));
  }

  /**
   * Constructor
   *
   * @param serviceNetwork       to use
   * @param routedServices       to use
   * @param zoning               to use
   * @param modeMappingData functionality and utils on mode mapping between GTFS and PLANit modes
   * @param serviceNodeToGtfsStopIdMapping mapping to use
   * @param gtfsStopIdToTransferZoneMapping mapping to use
   * @param gtfsStopIdToDiscardIssueMapping what a GTFS stop was discarded for by the preceding stages, if anything
   * @param profiler             to track integration statistics and diagnostics into
   * @param physicalCost         to use for cost provision
   * @param eligibleServiceModes to use for creation of link segment costs per mode
   */
  public AStarBatchExecutionData(
          ServiceNetwork serviceNetwork,
          RoutedServices routedServices,
          Zoning zoning,
          GtfsConverterModeMappingData modeMappingData,
          Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping,
          Function<String, TransferZone> gtfsStopIdToTransferZoneMapping,
          Function<String, GtfsParseIssue> gtfsStopIdToDiscardIssueMapping,
          GtfsIntegrationProfiler profiler,
          AbstractPhysicalCost physicalCost,
          Collection<Mode> eligibleServiceModes){

    this.serviceNetwork = serviceNetwork;
    this.modeMappingData = modeMappingData;
    this.serviceNodeToGtfsStopIdMapping = serviceNodeToGtfsStopIdMapping;
    this.gtfsStopIdToTransferZoneMapping = gtfsStopIdToTransferZoneMapping;
    this.gtfsStopIdToDiscardIssueMapping = gtfsStopIdToDiscardIssueMapping;
    this.profiler = profiler;


    // initialise and cach shared reusable data
    {
      var physicalNetwork = serviceNetwork.getParentNetwork();

      // constant and fixed across all threads, so cache once and reuse
      this.idIndexedVerticesAllLayers =
              TransportModelNetworkUtils.createIdIndexedVerticesAllLayers(physicalNetwork, zoning.getVirtualNetwork());

      // constant and fixed across all threads, so cache once and reuse
      this.linkSegmentCostsByMode = new HashMap<>();
      this.heuristicMultiplierByMode = new HashMap<>();
      for(var mode : eligibleServiceModes) {
        /* populate based on cost configuration and underlying physical network's link segments and connectoids */
        double[] modalLinkSegmentCosts = CostUtils.createAndPopulateModalSegmentCost(
                mode, physicalCost, physicalNetwork);
        linkSegmentCostsByMode.put(mode, modalLinkSegmentCosts);

        double heuristicMultiplier = Math.min(
                1.0 / mode.getMaximumSpeedKmH(), physicalNetwork.getLayerByMode(mode).findMaximumPaceHKm(mode));
        heuristicMultiplierByMode.put(mode, heuristicMultiplier);
      }

      this.connectoidsByAccessZone = zoning.getTransferConnectoids().createIndexByAccessZone();

      /* infer the modes for each service leg based on the routed services that use it, this reduced the complexity of
       * finding paths  for a leg and allows for validity check, in case routes with different PLANit modes use the same
       * leg (which we do not allow) essentially, each mode potentially obtains its own service network in terms of legs
       * and leg segments when they would use different physical routes between service nodes */
      this.serviceLegToModeMapping = new HashMap<>();
      routedServices.getLayers().forEach(l -> l.forEach(rs -> rs.forEach(
              s -> s.getTripInfo().getLegSegmentsStream().forEach(
                      ls -> serviceLegToModeMapping.put(ls.getParent(), s.getMode())))));
    }
  }

  public ServiceNetwork getServiceNetwork() {
    return serviceNetwork;
  }

  public Function<ServiceNode, String> getServiceNodeToGtfsStopIdMapping(){
    return serviceNodeToGtfsStopIdMapping;
  }

  public  Function<String, TransferZone> getGtfsStopIdToTransferZoneMapping(){
    return gtfsStopIdToTransferZoneMapping;
  }

  /**
   * Collect what a GTFS stop was discarded for by the stages preceding this integration
   *
   * @return mapping from GTFS stop id to the issue it was discarded for, yielding null where it was not
   */
  public Function<String, GtfsParseIssue> getGtfsStopIdToDiscardIssueMapping(){
    return gtfsStopIdToDiscardIssueMapping;
  }

  /**
   * Collect the profiler to track integration statistics and diagnostics into
   *
   * @return profiler
   */
  public GtfsIntegrationProfiler getProfiler(){
    return profiler;
  }

  /**
   * Determine the expected mode to be used for a given service leg
   *
   * @param serviceLeg to find mode for based on routed services that use it
   * @return mode, null if entry does not exist
   */
  public Mode getExpectedModeForServiceLeg(ServiceLeg serviceLeg){
    return serviceLegToModeMapping.get(serviceLeg);
  }


  /**
   * Collect PLANit mode if it is known as being activated, otherwise return null
   *
   * @param gtfsMode to check for
   * @return PLANit mode
   */
  public Mode getPrimaryPlanitModeIfActivated(RouteType gtfsMode){
    return modeMappingData.getPrimaryPlanitModeIfActivated(gtfsMode);
  }

  /**
   * Collect PLANit modes if it is known as being activated and compatible, otherwise return null
   *
   * @param gtfsMode to check for
   * @return all compatible PLANit modes in order from primary compatible to alternatives that one might consider,
   * null if not present
   */
  public List<Mode> getCompatiblePlanitModesIfActivated(RouteType gtfsMode){
    return modeMappingData.getCompatiblePlanitModesIfActivated(gtfsMode);
  }

  /**
   * Collect compatible PLANit modes from a given PLANit mode (if any). These only exist if a GTFS mode listed more
   * than one mapped PLANit mode, e.g. lightrail and tram, in which case lightrail would return tram and vice versa.
   *
   * @param planitMode to check for
   * @return all compatible PLANit modes
   */
  public Set<Mode> getCompatiblePlanitModesIfActivated(Mode planitMode){
    return modeMappingData.getCompatiblePlanitModesIfActivated(planitMode);
  }

  /**
   * Expand the mode to all compatible modes (if any) including the mode itself
   *
   * @param planitMode to expand
   * @return original mode supplemented with any compatible modes
   */
  public Set<Mode> expandWithCompatibleModes(Mode planitMode){
    return modeMappingData.expandWithCompatibleModes(planitMode);
  }

  public Set<TransferConnectoid> getConnectoidsByAccessZone(TransferZone transferZone) {
    return connectoidsByAccessZone.get(transferZone);
  }


  /**
   * Collect shortest path algorithm in a thread isolated way
   *
   * @param mode to get algo for
   * @return A* shortest path algo for current thread
   */
  public ShortestPathAStar getShortestPathAlgoForCurrentThread(Mode mode) {
    var mapForThread = shortestPathAlgoByModePerThread.get();
    return mapForThread.computeIfAbsent(mode,
            this::createNewShortestPathAlgo);
  }

}
