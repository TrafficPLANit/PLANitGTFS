package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.algorithms.shortest.ShortestPathAStar;
import org.goplanit.algorithms.shortest.ShortestPathResult;
import org.goplanit.component.PlanitComponentFactory;
import org.goplanit.cost.CostUtils;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.network.transport.TransportModelNetwork;
import org.goplanit.path.SimpleDirectedPathFactoryImpl;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.ServiceNetworkLayer;
import org.goplanit.utils.network.layer.service.ServiceLegSegment;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Integrates the service network and routed services (GTFS iterary) with the physical road network and zoning (GTFS stop based transfer zones)
 *
 * @author markr
 */
public class GtfsServicesAndZoningReaderIntegrator {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesAndZoningReaderIntegrator.class.getCanonicalName());

  /** settings from the parent reader used */
  private final GtfsIntermodalReaderSettings settings;
  private final Zoning zoning;
  private final ServiceNetwork serviceNetwork;
  private final RoutedServices services;

  private final Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping;

  private final Function<String, TransferZone> gtfsStopIdToTransferZoneMapping;

  // local data during execution
  private Map<Zone, Set<DirectedConnectoid>> connectoidsByAccessZone;

  /** shortest path algorithm used specific to each mode (and its link segment costs) */
  private Map<Mode, ShortestPathAStar> shortestPathAlgoByMode;

  /** Modes considered eligible for the service network based on activated modes during GTFs parsing and the modes available on the PLANit network */
  private Collection<Mode> eligibleServiceModes;

  /**
   * Initialise some local indices that are to be used
   */
  private void initialise(){
    this.connectoidsByAccessZone = zoning.getTransferConnectoids().createIndexByAccessZone();
    this.shortestPathAlgoByMode = new HashMap<>();

    var network = settings.getReferenceNetwork();
    var idToken = network.getIdGroupingToken();

    /* determine eligible service modes by intersecting physical layer modes with activate public transport modes of the GTFS settings */
    this.eligibleServiceModes = serviceNetwork.getTransportLayers().getSupportedModes();
    eligibleServiceModes.retainAll(settings.getServiceSettings().getAcivatedPlanitModes());

    /* prep shortest path algorithm (costs) per mode across network link segments for path searching, since costs are fixed, we can do this beforehand and reuse */
    for(var mode : eligibleServiceModes) {
      /* costs used for physical component of road network based on user settings */
      var physicalCostApproach =
          PlanitComponentFactory.create(
              AbstractPhysicalCost.class, settings.getStopToStopPathSearchPhysicalCostApproach(), new Object[]{ idToken});

      /* populate based on cost configuration and underlying physical network's link segments and connectoids */
      double[]  modalLinkSegmentCosts = CostUtils.createModalSegmentCost(mode, physicalCostApproach, network);

      int numberOfVerticesAllLayers = TransportModelNetwork.getNumberOfVerticesAllLayers(network, this.zoning);
      double heuristicMultiplier = Math.min(1.0/mode.getMaximumSpeedKmH(),network.getLayerByMode(mode).findMaximumPaceHKm(mode));
      shortestPathAlgoByMode.put(mode, new ShortestPathAStar(modalLinkSegmentCosts, numberOfVerticesAllLayers,  network.getCoordinateReferenceSystem(), heuristicMultiplier));
    }


  }

  /**
   * Get the connectod for given transfer zone, where we only allow a single connectoid at this point
   *
   * @param gtfsStopId provided for logging purposes
   * @param transferZone       to use
   * @return connectoid found
   */
  private DirectedConnectoid findTransferZoneConnectoid(String gtfsStopId, TransferZone transferZone) {
    var transferZoneConnectoids = connectoidsByAccessZone.get(transferZone);
    if(transferZoneConnectoids.stream().map( c -> c.getAccessNode()).distinct().count() >1){
      LOGGER.warning(String.format("Multiple access nodes found for the same PLANit transfer zone for GTFS stop %s, GTFS based transfer zones are expected to only have a single physical access node, verify correctness", gtfsStopId));
    }
    return transferZoneConnectoids.stream().findFirst().get();
  }

  /**
   * Perform the integration for a given service layer's service leg's leg segment,
   * where we identify a path on the physical network between the service nodes
   *
   * @param layer the segment resides in
   * @param legSegment between two service nodes
   */
  private void integrateLegSegment(ServiceNetworkLayer layer, ServiceLegSegment legSegment){
    var gtfsStopIdUpstream = serviceNodeToGtfsStopIdMapping.apply(legSegment.getUpstreamServiceNode());
    TransferZone transferZoneUpstream = gtfsStopIdToTransferZoneMapping.apply(gtfsStopIdUpstream);

    var gtfsStopIdDownstream = serviceNodeToGtfsStopIdMapping.apply(legSegment.getDownstreamServiceNode());
    TransferZone transferZoneDownstream = gtfsStopIdToTransferZoneMapping.apply(gtfsStopIdDownstream);
    if(transferZoneUpstream==null || transferZoneDownstream == null){
      /* likely no mapping found for stops due to physical network not being close enough, i.e., routes/legs/nodes fall outside bounding box of physical network we are mapping to */
      return;
    }

    /* link service node to transfer zone access node (which is a physical node) */
    var upstreamConnectoid = findTransferZoneConnectoid(gtfsStopIdUpstream, transferZoneUpstream);
    var downstreamConnectoid = findTransferZoneConnectoid(gtfsStopIdDownstream, transferZoneDownstream);
    for(var mode : eligibleServiceModes) {
      /* transfer zone mode compatibility */
      if (!(upstreamConnectoid.isModeAllowed(transferZoneUpstream, mode) && downstreamConnectoid.isModeAllowed(transferZoneDownstream, mode))) {
        continue;
      }

      /* connectoid access link segment mode compatibility */
      if (!(upstreamConnectoid.getAccessLinkSegment().isModeAllowed(mode) && downstreamConnectoid.getAccessLinkSegment().isModeAllowed(mode))) {
        continue;
      }

      var shortestPathAlgo = shortestPathAlgoByMode.get(mode);
      // find shortest path using the upstream access node and downstream access link segment upstream node to ensure that we use both access link segments in the final path
      // we then supplement the found path with the two access link segments which we know are mode compatible
      ShortestPathResult result = shortestPathAlgo.executeOneToOne(upstreamConnectoid.getAccessNode(), downstreamConnectoid.getAccessLinkSegment().getUpstreamNode());
      var simplePath = result.createPath(new SimpleDirectedPathFactoryImpl(),upstreamConnectoid.getAccessNode(),downstreamConnectoid.getAccessLinkSegment().getUpstreamNode());
      LOGGER.info(simplePath.toString());
      //TODO continue here by using the simplepath result to link the leg segment to the physical network by attaching its macroscopic link segments to it! --> THEN TEST

    }

  }

  /**
   * Validate inputs to see if integration and creation of physical paths and relating them to the service network is supported
   */
  private void validateInputs() {
    PlanItRunTimeException.throwIfNull(this.serviceNodeToGtfsStopIdMapping, "serviceNodeToGtfsStopIdMapping is null");
    PlanItRunTimeException.throwIfNull(this.gtfsStopIdToTransferZoneMapping, "gtfsStopIdToTransferZoneMapping is null");
    PlanItRunTimeException.throwIfNull(this.serviceNetwork, "serviceNetwork is null");
    PlanItRunTimeException.throwIfNull(this.settings, "GTFS Intermodal reader settings is null");
    PlanItRunTimeException.throwIfNull(this.zoning, "zoning is null");
    PlanItRunTimeException.throwIfNull(this.services, "routed services is null");

    //todo: multiple layers should be possible to implement but at this point simply has not been done due to absence of a case where this is used
    PlanItRunTimeException.throwIf(settings.getReferenceNetwork().getTransportLayers().size()>1, "Currently GTFS converter only supports physical reference networks with a single layer");
    PlanItRunTimeException.throwIf(serviceNetwork.getTransportLayers().size()>1, "Currently GTFS converter only supports service networks with a single layer");
  }

  /**
   * Constructor
   *
   * @param settings of the parent reader used
   * @param zoning to integrate
   * @param serviceNetwork to integrate
   * @param services to integrate
   * @param serviceNodeToGtfsStopIdMapping mapping from PLANit service nodes to GTFS stop ids
   * @param gtfsStopIdToTransferZoneMapping mapping from GTFS stop id to PLANit transfer zone
   */
  public GtfsServicesAndZoningReaderIntegrator(
      GtfsIntermodalReaderSettings settings,
      Zoning zoning,
      ServiceNetwork serviceNetwork,
      RoutedServices services,
      Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping,
      Function<String, TransferZone> gtfsStopIdToTransferZoneMapping) {
    this.settings = settings;
    this.zoning = zoning;
    this.serviceNetwork = serviceNetwork;
    this.services = services;
    this.serviceNodeToGtfsStopIdMapping = serviceNodeToGtfsStopIdMapping;
    this.gtfsStopIdToTransferZoneMapping = gtfsStopIdToTransferZoneMapping;
    validateInputs();
  }

  /**
   * Perform the integration where we identify paths between each of the used GTFS stop service nodes on the physical road network and update
   * the PLANit references in the service legs accordingly
   */
  public void execute() {
    initialise();

    /* process service leg segments */
    serviceNetwork.getTransportLayers().forEach( layer -> layer.getLegs().forEach(leg -> leg.forEachSegment( legSegment -> integrateLegSegment(layer, (ServiceLegSegment) legSegment))));

    //todo: prune service network and routes for unmatched service nodes (and routes) --> many routes/service nodes/legs might not have been matched due to network
    //      not covering entire GTFS area in which case we should remove them as it does result in an invalid/incomplete routedservices/servicenetwork
  }

  /**
   * Reset internal (temporary) state
   */
  public void reset(){
    this.connectoidsByAccessZone = null;
    this.shortestPathAlgoByMode = null;
  }
}
