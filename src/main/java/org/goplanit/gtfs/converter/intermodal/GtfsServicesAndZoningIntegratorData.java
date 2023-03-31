package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.algorithms.shortest.ShortestPathAStar;
import org.goplanit.component.PlanitComponentFactory;
import org.goplanit.cost.CostUtils;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.gtfs.converter.GtfsConverterHandlerData;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.network.transport.TransportModelNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.service.ServiceLeg;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;

import java.util.*;
import java.util.logging.Logger;

/**
 * Data for integration where we create some local mappings based on the mode mapping from the settings among other things
 */
public class GtfsServicesAndZoningIntegratorData {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesAndZoningIntegratorData.class.getCanonicalName());

  /** zoning to use */
  private final Zoning zoning;

  /** routed services to use */
  private final RoutedServices routedServices;

  private final GtfsIntermodalReaderSettings settings;

  // local data during execution
  private Map<Zone, Set<DirectedConnectoid>> connectoidsByAccessZone;

  /** shortest path algorithm used specific to each mode (and its link segment costs) */
  private Map<Mode, ShortestPathAStar> shortestPathAlgoByMode;

  /** track the expected mode to be used for a given service leg (before physical link segments have been attached), based on
   * the routed services that traverse it (which do have a mode) */
  private Map<ServiceLeg, Mode> serviceLegToModeMapping;

  /** use this for the mode mapping */
  private GtfsConverterHandlerData modeMappingData;

  /**
   * Initialise a shortest path algorithm with free flow costs for the entire network for a given mode so it can be reused when needed
   *
   * @param mode to prep algorithm for (stored in class member)
   */
  private void initialiseShortestPathAlgorithmForMode(Mode mode) {
    var network = getServiceNetwork().getParentNetwork();
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
   * Constructor
   *
   * @param serviceNetwork to use
   * @param routedServices to use
   * @param zoning to use
   * @param settings to extract mode mapping from
   */
  protected GtfsServicesAndZoningIntegratorData(
      ServiceNetwork serviceNetwork,
      RoutedServices routedServices,
      Zoning zoning,
      GtfsIntermodalReaderSettings settings) {
    this.modeMappingData = new GtfsConverterHandlerData(serviceNetwork, settings.getServiceSettings());
    this.settings = settings;
    this.zoning = zoning;
    this.routedServices = routedServices;
  }

  /**
   * Initialise before start of integration on the owner of this instance. Only after initialisation the available public getters/settings can be used
   */
  public void initialise() {
    this.connectoidsByAccessZone = zoning.getTransferConnectoids().createIndexByAccessZone();

    /* determine eligible service modes by intersecting physical layer modes with activated public transport modes of the GTFS settings */
    var eligibleServiceModes = getServiceNetwork().getTransportLayers().getSupportedModes();
    eligibleServiceModes.retainAll(getActivatedPlanitModes());
    if(eligibleServiceModes.isEmpty()){
      LOGGER.severe("No eligible modes found on any of the service network layers that are configured as activated for the GTFS reader, consider revising your configuration");
    }

    /* prep shortest path algorithm (costs) per mode across network link segments for path searching, since costs are fixed, we can do this beforehand and reuse */
    this.shortestPathAlgoByMode = new HashMap<>();
    for(var mode : eligibleServiceModes) {
      initialiseShortestPathAlgorithmForMode(mode);
    }

    /* infer the modes for each service leg based on the routed services that use it, this reduced the complexity of finding paths
    *  for a leg and allows for validity check, in case routes with different PLANit modes use the same leg (which we do not allow)
    *  essentially, each mode potentially obtains its own service network in terms of legs and leg segments when they would use different
    *  physical routes between service nodes */
    serviceLegToModeMapping = new HashMap<>();
    this.routedServices.getLayers().forEach(l -> l.forEach( rs -> rs.forEach(
        s -> s.getTripInfo().getLegSegmentsStream().forEach(ls -> serviceLegToModeMapping.put(ls.getParent(), s.getMode())))));
  }

  /** Access to the service network
   * @return the service network being populated
   */
  public ServiceNetwork getServiceNetwork() {
    return modeMappingData.getServiceNetwork();
  }

  public Zoning getZoning() {
    return zoning;
  }

  /** activated planit modes, note that initialise should have been called before this is populated
   * @return activated planit mode instances including predefined mode versions
   */
  public Collection<Mode> getActivatedPlanitModes(){
    return modeMappingData.getActivatedPlanitModesByGtfsMode();
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
   * @return all compatible PLANit modes in order from primary compatible to alternatives that one might consider, null if not present
   */
  public List<Mode> getCompatiblePlanitModesIfActivated(RouteType gtfsMode){
    return modeMappingData.getCompatiblePlanitModesIfActivated(gtfsMode);
  }

  /**
   * Collect compatible PLANit modes from a given PLANit mode (if any). These only exist if a GTFS mode listed more than one
   * mapped PLANit mode, e.g. lightrail and tram, in which case lightrail would return tram and vice versa.
   *
   * @param planitMode to check for
   * @return all compatible PLANit modes
   */
  public Set<Mode> getCompatiblePlanitModesIfActivated(Mode planitMode){
    return modeMappingData.getCompatiblePlanitModesIfActivated(planitMode);
  }

  public GtfsIntermodalReaderSettings getSettings() {
    return settings;
  }

  public Set<DirectedConnectoid> getConnectoidsByAccessZone(TransferZone transferZone) {
    return connectoidsByAccessZone.get(transferZone);
  }

  /**
   * Shortest path algorithm by mode initialised with the costs for that mode per link segment,
   * requires {@link #initialise()} to be invoked beforehand
   *
   * @param mode to get shortest path algorithm for
   * @return algo
   */
  public ShortestPathAStar getShortestPathAlgoByMode(Mode mode) {
    return shortestPathAlgoByMode.get(mode);
  }

  public void reset() {
    this.connectoidsByAccessZone = null;
    this.shortestPathAlgoByMode = null;
  }
}
