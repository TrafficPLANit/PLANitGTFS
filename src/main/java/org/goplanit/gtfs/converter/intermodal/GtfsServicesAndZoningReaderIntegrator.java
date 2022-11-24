package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.algorithms.shortest.ShortestPathDijkstra;
import org.goplanit.component.PlanitComponentFactory;
import org.goplanit.cost.CostUtils;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.cost.physical.FreeFlowLinkTravelTimeCost;
import org.goplanit.cost.virtual.FixedConnectoidTravelTimeCost;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.network.layer.ServiceNetworkLayer;
import org.goplanit.utils.network.layer.service.ServiceLeg;
import org.goplanit.utils.network.layer.service.ServiceLegSegment;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.goplanit.cost.CostUtils.createEmptyLinkSegmentCostArray;

/**
 * Integrates the service network and routed services (GTFS iterary) with the physical road network and zoning (GTFS stop based transfer zones)
 *
 * @author markr
 */
public class GtfsServicesAndZoningReaderIntegrator {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesAndZoningReaderIntegrator.class.getCanonicalName());

  private final MacroscopicNetwork network;
  private final Zoning zoning;
  private final ServiceNetwork serviceNetwork;
  private final RoutedServices services;
  private final Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping;

  private final Function<String, TransferZone> gtfsStopIdToTransferZoneMapping;

  // local data during execution
  private Map<Zone, Set<DirectedConnectoid>> connectoidsByAccessZone;

  /**
   * Initialise some local indices that are to be used
   */
  private void initialise(){
    this.connectoidsByAccessZone = zoning.getTransferConnectoids().createIndexByAccessZone();
  }
  /**
   * Perform the integration for a given service layer's service leg's leg segment,
   * where we identify a path on the physical network between the service nodes
   *
   * @param legSegment between two service nodes
   */
  private void integrateLegSegment(ServiceLegSegment legSegment){
    var gtfsStopIdUpstream = serviceNodeToGtfsStopIdMapping.apply(legSegment.getUpstreamServiceNode());
    TransferZone transferZoneUpstream = gtfsStopIdToTransferZoneMapping.apply(gtfsStopIdUpstream);

    var gtfsStopIdDownstream = serviceNodeToGtfsStopIdMapping.apply(legSegment.getDownstreamServiceNode());
    TransferZone transferZoneDownstream = gtfsStopIdToTransferZoneMapping.apply(gtfsStopIdDownstream);

    /* link service node to transfer zone access node (which is a physical node) */
    var upstreamTransferZoneConnectoids = connectoidsByAccessZone.get(transferZoneUpstream);
    if(upstreamTransferZoneConnectoids.stream().map( c -> c.getAccessNode()).distinct().count() >1){
      LOGGER.warning(String.format("Multiple access nodes found for the same PLANit transfer zone for GTFS stop %s, GTFS based transfer zones are expected to only have a single physical access node, verify correctness", gtfsStopIdUpstream));
    }
    var upstreamAccessNode = upstreamTransferZoneConnectoids.stream().findFirst().get().getAccessNode();
    var downstreamTransferZoneConnectoids = connectoidsByAccessZone.get(transferZoneDownstream);
    if(downstreamTransferZoneConnectoids.stream().map(c -> c.getAccessNode()).distinct().count() >1){
      LOGGER.warning(String.format("Multiple access nodes found for the same PLANit transfer zone for GTFS stop %s, GTFS based transfer zones are expected to only have a single physical access node, verify correctness", gtfsStopIdDownstream));
    }
    var downStreamAccessNode = downstreamTransferZoneConnectoids.stream().findFirst().get().getAccessNode();
    /* because for GTFS each GTFS stop only has a single mode attached to it, we assume all connectoids are eligible to
    *  construct a path for. We will then choose the most likely path if multiple paths are found */

    double[] freeFlowCosts = CostUtils.createEmptyLinkSegmentCostArray(this.network, this.zoning);

    //todo configuration of GTFS intermodal reader should allow for the type of the costs to use which for now is fixed to free flow and fixed connectoid cost
    // in future we can then alter this by letting users provide ways to provide custom costs or ways to use the GTFS shapes to funnel through the costs

    //todo -> then update the function passed in here. It is now null and not correct for mapping gtfsStopIdToTransferZoneMapping
    var freeflowCost = PlanitComponentFactory.createWithListeners(AbstractPhysicalCost.class, FreeFlowLinkTravelTimeCost.class, new Object[]{ network.getIdGroupingToken()}, null);

  }

  /**
   * Constructor
   * @param network to integrate
   * @param zoning to integrate
   * @param serviceNetwork to integrate
   * @param services to integrate
   * @param serviceNodeToGtfsStopIdMapping mapping from PLANit service nodes to GTFS stop ids
   * @param gtfsStopIdToTransferZoneMapping mapping from GTFS stop id to PLANit transfer zone
   */
  public GtfsServicesAndZoningReaderIntegrator(
      MacroscopicNetwork network, Zoning zoning, ServiceNetwork serviceNetwork, RoutedServices services,
      Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping,
      Function<String, TransferZone> gtfsStopIdToTransferZoneMapping) {
    this.network = network;
    this.zoning = zoning;
    this.serviceNetwork = serviceNetwork;
    this.services = services;
    this.serviceNodeToGtfsStopIdMapping = serviceNodeToGtfsStopIdMapping;
    this.gtfsStopIdToTransferZoneMapping = gtfsStopIdToTransferZoneMapping;
  }

  /**
   * Perform the integration where we identify paths between each of the used GTFS stop service nodes on the physical road network and update
   * the PLANit references in the service legs accordingly
   */
  public void execute() {
    initialise();
    serviceNetwork.getTransportLayers().forEach( layer -> layer.getLegs().forEach(leg -> leg.forEachSegment( legSegment -> integrateLegSegment((ServiceLegSegment) legSegment))));
  }
}
