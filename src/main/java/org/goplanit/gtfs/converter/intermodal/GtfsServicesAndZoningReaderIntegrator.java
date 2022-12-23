package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.algorithms.shortest.ShortestPathAStar;
import org.goplanit.algorithms.shortest.ShortestPathResult;
import org.goplanit.component.PlanitComponentFactory;
import org.goplanit.cost.CostUtils;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.network.transport.TransportModelNetwork;
import org.goplanit.path.SimpleDirectedPathFactoryImpl;
import org.goplanit.path.SimpleDirectedPathImpl;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.ServiceNetworkLayer;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.network.layer.service.ServiceLegSegment;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.path.SimpleDirectedPath;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Integrates the service network and routed services (GTFS itinerary) with the physical road network and zoning (GTFS stop based transfer zones)
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
      double[]  modalLinkSegmentCosts = CostUtils.createAndPopulateModalSegmentCost(mode, physicalCostApproach, network);

      int numberOfVerticesAllLayers = TransportModelNetwork.getNumberOfVerticesAllLayers(network, this.zoning);
      double heuristicMultiplier = Math.min(1.0/mode.getMaximumSpeedKmH(),network.getLayerByMode(mode).findMaximumPaceHKm(mode));
      shortestPathAlgoByMode.put(mode, new ShortestPathAStar(modalLinkSegmentCosts, numberOfVerticesAllLayers,  network.getCoordinateReferenceSystem(), heuristicMultiplier));
    }


  }

  /**
   * Get the connectoids for given transfer zone, grouped by unique access nodes (as multiple access nodes across more than one
   * connectoid might exist)
   *
   * @param gtfsStopId provided for logging purposes
   * @param transferZone       to use
   * @return connectoids found, grouped by access node
   */
  private Map<Node, List<DirectedConnectoid>> findTransferZoneConnectoidsGroupByAccessNode(String gtfsStopId, TransferZone transferZone) {
    var transferZoneConnectoids = connectoidsByAccessZone.get(transferZone);
    /* it is possible multiple connectoids exist, e.g., train platforms with access on both sides in either direction, therefore we group by
     * access node */
    return transferZoneConnectoids.stream().collect(Collectors.groupingBy(c -> c.getAccessNode()));
  }

  /**
   * Perform the integration for a given service layer's service leg's leg segment,
   * where we identify a path on the physical network between the service nodes. Note that we create service paths
   * for all eligible pt modes on the layer/segment regardless if an actual trip takes place between the leg segment stops.
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


    for (var mode : eligibleServiceModes) {
      if (!layer.supports(mode)) {
        continue;
      }
      var shortestPathAlgo = shortestPathAlgoByMode.get(mode);

      /* link service node to transfer zone access node (which is a physical node) */
      var upstreamConnectoidsByAccessNode = findTransferZoneConnectoidsGroupByAccessNode(gtfsStopIdUpstream, transferZoneUpstream);
      var downstreamConnectoidsByAccessNode = findTransferZoneConnectoidsGroupByAccessNode(gtfsStopIdDownstream, transferZoneDownstream);

      // only proceed if at least a single connectoid on any access node supports the current mode as well
      if( !upstreamConnectoidsByAccessNode.values().stream().anyMatch( l -> l.stream().anyMatch( c -> c.isModeAllowed(transferZoneUpstream, mode))) ||
          !downstreamConnectoidsByAccessNode.values().stream().anyMatch( l -> l.stream().anyMatch( c -> c.isModeAllowed(transferZoneDownstream, mode)))){
        continue;
      }

      Set<SimpleDirectedPath> allLegSegmentPathOptions = new HashSet<>();
      for(var upstreamAccessNodeConnectoidsEntry : upstreamConnectoidsByAccessNode.entrySet()) {
        for (var downstreamAccessNodeConnectoidsEntry : downstreamConnectoidsByAccessNode.entrySet()) {

          // find eligible paths between upstream access node and downstream access node(s).
          Set<SimpleDirectedPath> accessNodePathOptions =
                  createShortestPathsbetweenAccessNodes(
                          mode,
                          upstreamAccessNodeConnectoidsEntry.getValue(),
                          transferZoneUpstream,
                          downstreamAccessNodeConnectoidsEntry.getValue(),
                          transferZoneDownstream,
                          shortestPathAlgo);
          allLegSegmentPathOptions.addAll(accessNodePathOptions);
        }
      }

      // when no options are found but connectoids support current mode, issue a warning
      if(allLegSegmentPathOptions.isEmpty()){
        LOGGER.warning(String.format("Unable to find physical path between GTFS stop %s and GTFS stop %s on underlying PLANit network", gtfsStopIdUpstream, gtfsStopIdDownstream));
      }
      //TODO: NOTE: We can have multiple paths still despite this being a call for a single leg segment. This is because it is possible that the related transfer zone of the
      //      service node may represent multiple stops (and service nodes). Therefore we must make an educated guess how to link the leg segment (and service node) to the found
      //      which of the found paths if multiple exist. Once a choice has been made, we will then encounter another leg segment later on which will generate the same paths but now
      //      should be matched to the remaining (other) path. This likely ONLY happens for consecutive train stations with platforms having tracks on both sides, e.g. redfern and central
      //      RULE --> use rule of thumb where we use the shortest path (this will eliminate crossing paths most likely (switches), we then
      //      might still choose the wrong platform/track but this is not a big issue.

      //TODO continue here by using the simplepath result to link the leg segment to the physical network by attaching its macroscopic link segments to it! --> THEN TEST

      //TODO Only ~45 paths created. Should be many more, seem to be too few leg segments as there should be many more given that
      // we have abound 85 transfer zones in the network, each with at least a single connectoid
      for(var foundPath : allLegSegmentPathOptions) {
        LOGGER.info(mode.getName() + " " + StreamSupport.stream(foundPath.spliterator(), false).map(e -> e.getParent().getExternalId()).collect(Collectors.joining(", ")));
      }
    }
  }

  private Set<SimpleDirectedPath> createShortestPathsbetweenAccessNodes(
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
          ShortestPathResult result = shortestPathAlgo.executeOneToOne(upstreamConnectoid.getAccessNode(), downstreamConnectoid.getAccessLinkSegment().getUpstreamNode());
          var foundPath = (SimpleDirectedPathImpl) result.createPath(new SimpleDirectedPathFactoryImpl(), upstreamConnectoid.getAccessNode(), downstreamConnectoid.getAccessLinkSegment().getUpstreamNode());

          /* no u-turn allowed between access link segment and first link on path */
          if(foundPath!=null && !foundPath.isEmpty() && foundPath.iterator().next().equals(upstreamConnectoid.getAccessLinkSegment().getOppositeDirectionSegment())){
            continue;
          }
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
