package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.algorithms.shortest.ShortestPathAStar;
import org.goplanit.algorithms.shortest.ShortestPathResult;
import org.goplanit.component.PlanitComponentFactory;
import org.goplanit.cost.CostUtils;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.network.layer.service.ServiceLegSegmentImpl;
import org.goplanit.network.transport.TransportModelNetwork;
import org.goplanit.path.SimpleDirectedPathFactoryImpl;
import org.goplanit.path.SimpleDirectedPathImpl;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.misc.IterableUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.RoutedServiceLayer;
import org.goplanit.utils.network.layer.physical.Node;
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
   * Initialise a shortest path algorithm with free flow costs for the entire network for a given mode so it can be reused when needed
   *
   * @param mode to prep algorithm for (stored in class member)
   */
  private void initialiseShortestPathAlgorithmForMode(Mode mode) {
    var network = settings.getReferenceNetwork();
    var idToken = network.getIdGroupingToken();

    /* costs used for physical component of road network based on user settings */
    var physicalCostApproach =
        PlanitComponentFactory.create(
            AbstractPhysicalCost.class, settings.getStopToStopPathSearchPhysicalCostApproach(), new Object[]{ idToken});

    /* populate based on cost configuration and underlying physical network's link segments and connectoids */
    double[]  modalLinkSegmentCosts = CostUtils.createAndPopulateModalSegmentCost(mode, physicalCostApproach, network);

    int numberOfVerticesAllLayers = TransportModelNetwork.getNumberOfVerticesAllLayers(network, this.zoning);
    double heuristicMultiplier = Math.min(1.0/mode.getMaximumSpeedKmH(),network.getLayerByMode(mode).findMaximumPaceHKm(mode));
    this.shortestPathAlgoByMode.put(mode, new ShortestPathAStar(modalLinkSegmentCosts, numberOfVerticesAllLayers,  network.getCoordinateReferenceSystem(), heuristicMultiplier));
  }

  /**
   * Initialise some local indices that are to be used
   */
  private void initialise(){
    this.connectoidsByAccessZone = zoning.getTransferConnectoids().createIndexByAccessZone();

    /* determine eligible service modes by intersecting physical layer modes with activate public transport modes of the GTFS settings */
    this.eligibleServiceModes = serviceNetwork.getTransportLayers().getSupportedModes();
    eligibleServiceModes.retainAll(settings.getServiceSettings().getAcivatedPlanitModes());

    /* prep shortest path algorithm (costs) per mode across network link segments for path searching, since costs are fixed, we can do this beforehand and reuse */
    this.shortestPathAlgoByMode = new HashMap<>();
    for(var mode : eligibleServiceModes) {
      initialiseShortestPathAlgorithmForMode(mode);
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
  private Map<Node, List<DirectedConnectoid>> findTransferZoneConnectoidsGroupByAccessNode(
      String gtfsStopId, TransferZone transferZone, ServiceNode gtfsStopServiceNode) {
    var transferZoneConnectoids = connectoidsByAccessZone.get(transferZone);
    /* it is possible multiple connectoids exist, e.g., train platforms with access on both sides in either direction, therefore we group by
     * access node */
    var resultByAccessNode = transferZoneConnectoids.stream().collect(Collectors.groupingBy(c -> c.getAccessNode()));

    /* When GTFS stop has been linked to a service node which in turn has already been mapped to a physical node, then we must limit the connectoids we consider to
     * access nodes matching the physical node that is related to this service node */
    if(gtfsStopServiceNode.hasPhysicalParentNode()){
      resultByAccessNode.entrySet().removeIf( e -> e.getKey() != gtfsStopServiceNode.getPhysicalParentNode());
    }

    if(resultByAccessNode.isEmpty() && gtfsStopServiceNode.hasPhysicalParentNode()){
      LOGGER.severe(String.format("Unable to find available transfer zone access nodes for leg segment, likely GTFS stop %s mapped to incorrect physical access node upon earlier path search", gtfsStopId));
    }
    return resultByAccessNode;
  }

  /**
   * Given a network layer and two GTFS stop's transfer zones, find the most likely path between them taking the mode and shortest distance into account
   *
   * @param layer to use for the physical network
   * @param gtfsStopUpstreamServiceNode departure GTFS stop in service node form
   * @param gtfsStopDownstreamServiceNode arrival GTFS stop in service node form
   * @return found most likely physical path (if any, can be null)
   */
  private SimpleDirectedPath findMostLikelyPathBetweenGtfsStopServiceNodes(
          RoutedServiceLayer layer, ServiceNode gtfsStopUpstreamServiceNode, ServiceNode gtfsStopDownstreamServiceNode) {

    var gtfsStopIdUpstream = serviceNodeToGtfsStopIdMapping.apply(gtfsStopUpstreamServiceNode);
    TransferZone transferZoneUpstream = gtfsStopIdToTransferZoneMapping.apply(gtfsStopIdUpstream);

    var gtfsStopIdDownstream = serviceNodeToGtfsStopIdMapping.apply(gtfsStopDownstreamServiceNode);
    TransferZone transferZoneDownstream = gtfsStopIdToTransferZoneMapping.apply(gtfsStopIdDownstream);
    if(transferZoneUpstream==null || transferZoneDownstream == null){
      /* likely no mapping found for stops due to physical network not being close enough, i.e., routes/legs/nodes fall outside bounding box of physical network we are mapping to */
      return null;
    }

//    if(gtfsStopIdUpstream.equals("2000452") && gtfsStopIdDownstream.equals("2000449")){
//      int bla = 4;
//    }

    /* link service node to transfer zone access node (which is a physical node) */
    var upstreamConnectoidsByAccessNode = findTransferZoneConnectoidsGroupByAccessNode(gtfsStopIdUpstream, transferZoneUpstream, gtfsStopUpstreamServiceNode);
    var downstreamConnectoidsByAccessNode = findTransferZoneConnectoidsGroupByAccessNode(gtfsStopIdDownstream, transferZoneDownstream, gtfsStopDownstreamServiceNode);
    if(upstreamConnectoidsByAccessNode.isEmpty() || downstreamConnectoidsByAccessNode.isEmpty()){
      return null;
    }

    //todo: not great that we assume the first matching mode is the only service mode used by the leg segment. This assumes each GTFS stop ONLY ever services
    //      a single mode, if not this breaks...and our format as well, because we only support a single string of physical link segments for each service leg segment.
    //      to solve this we would need to either places these in seaprate layers (probably best) in which case this structure can be retained, or multiple service legs(segments) between
    //      the same service nodes should be allowed, where the service legs(segments) must become mode aware via a service leg segment type for example.
    SimpleDirectedPath chosenPath = null;
    for (var mode : eligibleServiceModes) {
      if (!layer.supports(mode)) {
        continue;
      }
      var shortestPathAlgo = shortestPathAlgoByMode.get(mode);

      // proceed for the first support mode when at least a single connectoid on any access node supports it
      if (!upstreamConnectoidsByAccessNode.values().stream().anyMatch(l -> l.stream().anyMatch(c -> c.isModeAllowed(transferZoneUpstream, mode))) ||
          !downstreamConnectoidsByAccessNode.values().stream().anyMatch(l -> l.stream().anyMatch(c -> c.isModeAllowed(transferZoneDownstream, mode)))) {
        continue;
      }

      if(chosenPath != null){
        throw new PlanItRunTimeException("Service leg segment is ambiguous, it supports more than a single mode which is not supported in PLANit yet (within a single layer)");
      }

      Set<SimpleDirectedPath> allLegSegmentPathOptions = new HashSet<>();
      for (var upstreamAccessNodeConnectoidsEntry : upstreamConnectoidsByAccessNode.entrySet()) {
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
      if (allLegSegmentPathOptions.isEmpty()) {
        return null;
      }

      chosenPath = allLegSegmentPathOptions.iterator().next();
      if (allLegSegmentPathOptions.size() > 1) {
        //  We can have multiple paths still despite this being a call for a single leg segment. This is because it is possible that the related transfer zone of the
        //  service node may represent multiple stops (and service nodes). Therefore, we must make an educated guess how to link the leg segment (and service node) to the found
        //  which of the found paths if multiple exist. Once a choice has been made, we will then encounter another leg segment later on which will generate the same paths but now
        //  should be matched to the remaining (other) path. This likely ONLY happens for consecutive train stations with platforms having tracks on both sides, e.g. redfern and central
        //  RULE --> use rule of thumb where we use the shortest path (this will eliminate crossing paths most likely (switches), we then
        //  might still choose the wrong platform/track but this is not a big issue.
        LOGGER.fine(String.format("Multiple paths possible between two GTFS stops (%s, %s) due to GTFS stop having multiple possible access points to physical network, e.g., train platform, choosing first", gtfsStopIdUpstream, gtfsStopIdDownstream));
        chosenPath = allLegSegmentPathOptions.stream().min(Comparator.comparingDouble(p -> p.computeLengthKm())).get();
      }
    }

    // print all subsquent (OSM) node external ids of each chosen path for visualisation/error checking purposes
    //LOGGER.info(mode.getName() + " " + chosenPath.iterator().next().getUpstreamVertex().getExternalId() + ","+ StreamSupport.stream(chosenPath.spliterator(), false).map(es -> es.getDownstreamVertex().getExternalId()).collect(Collectors.joining(", ")));
    return chosenPath;
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

          /* ban direct u-turn around access link segments as option */
          // todo if ever we support turn bans, then we must make the below more sophisticated
          Set<EdgeSegment> bannedLinkSegments = new HashSet<>();
          if(upstreamConnectoid.getAccessLinkSegment().getOppositeDirectionSegment() != null){
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
    PlanItRunTimeException.throwIfNull(this.serviceNetwork, "serviceNetwork is null");
    PlanItRunTimeException.throwIfNull(this.settings, "GTFS Intermodal reader settings is null");
    PlanItRunTimeException.throwIfNull(this.zoning, "zoning is null");
    PlanItRunTimeException.throwIfNull(this.services, "routed services is null");

    //todo: multiple layers should be possible to implement but at this point simply has not been done due to absence of a case where this is used
    PlanItRunTimeException.throwIf(settings.getReferenceNetwork().getTransportLayers().size()>1, "Currently GTFS converter only supports physical reference networks with a single layer");
    PlanItRunTimeException.throwIf(serviceNetwork.getTransportLayers().size()>1, "Currently GTFS converter only supports service networks with a single layer");
  }

  /**
   * Perform the integration for a given service layer's service leg's leg segment,
   * where we identify a path on the physical network between the service nodes. Note that we create service paths
   * for all eligible pt modes on the layer/segment regardless if an actual trip takes place between the leg segment stops.
   *
   * @param layer the segment resides in
   * @param legSegment between two service nodes that will be populated with physical link segments (references)
   */
  private void integrateLegSegment(RoutedServiceLayer layer, ServiceLegSegmentImpl legSegment){

    var chosenPath = findMostLikelyPathBetweenGtfsStopServiceNodes(layer, legSegment.getUpstreamServiceNode(), legSegment.getDownstreamServiceNode());
    if(chosenPath != null) {
      // now attach service nodes to physical network nodes
      integrateServiceNode(legSegment.getUpstreamServiceNode(), (Node) chosenPath.getFirstSegment().getUpstreamVertex());
      integrateServiceNode(legSegment.getDownstreamServiceNode(), (Node) chosenPath.getLastSegment().getDownstreamVertex());

      /* now attach the link segments to the service leg segment based on the found path */
      legSegment.setPhysicalParentSegments(IterableUtils.toTypeCastList(chosenPath));
      return;
    }

    /* check if we should provide a warning in case we expected a path, e.g., service nodes are mapped to physical network indicating it
     * (roughly) falls within the network's bounding box (otherwise these would not have been mapped to a physical network node either in which case it is logical
     * no match is found and no warning is given */
    if(legSegment.getUpstreamServiceNode().hasPhysicalParentNode() && legSegment.getDownstreamServiceNode().hasPhysicalParentNode()) {
      LOGGER.warning(String.format("Unable to find physical path between GTFS stop %s and GTFS stop %s on underlying PLANit network",
          serviceNodeToGtfsStopIdMapping.apply(legSegment.getUpstreamServiceNode()), serviceNodeToGtfsStopIdMapping.apply(legSegment.getDownstreamServiceNode())));
    }
  }

  /**
   * Given a service node and physical network node, connect the two such that mapping between physical and service network is made
   * appropriately
   *
   * @param serviceNode to relate to physicalNetworkNode when appropriate
   * @param physicalNetworkNode to attach to service node when appropriate
   */
  private void integrateServiceNode(ServiceNode serviceNode, Node physicalNetworkNode) {
    serviceNode.setPhysicalParentNode(physicalNetworkNode);
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

    /* process service leg segments - knowing that all leg segments are instances of ServiceLegSegmentImpl as this is how the GTFS converter has created them */
    serviceNetwork.getTransportLayers().forEach( layer -> layer.getLegs().forEach(leg -> leg.forEachSegment( legSegment -> integrateLegSegment(layer, (ServiceLegSegmentImpl) legSegment))));
  }

  /**
   * Reset internal (temporary) state
   */
  public void reset(){
    this.connectoidsByAccessZone = null;
    this.shortestPathAlgoByMode = null;
  }
}
