package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.algorithms.shortest.ShortestPathAStar;
import org.goplanit.component.PlanitComponentFactory;
import org.goplanit.cost.CostUtils;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.gtfs.converter.GtfsConverterHandlerData;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.network.transport.TransportModelNetwork;
import org.goplanit.utils.mode.Mode;
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

  private final GtfsIntermodalReaderSettings settings;

  // local data during execution
  private Map<Zone, Set<DirectedConnectoid>> connectoidsByAccessZone;

  /** shortest path algorithm used specific to each mode (and its link segment costs) */
  private Map<Mode, ShortestPathAStar> shortestPathAlgoByMode;

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
   * @param zoning to use
   * @param settings to extract mode mapping from
   */
  protected GtfsServicesAndZoningIntegratorData(ServiceNetwork serviceNetwork, Zoning zoning, GtfsIntermodalReaderSettings settings) {
    this.modeMappingData = new GtfsConverterHandlerData(serviceNetwork, settings.getServiceSettings());
    this.settings = settings;
    this.zoning = zoning;
  }

  /**
   * Initialise before start of integration on the owner of this instance. Only after initialisation the availabe public getters/settings can be used
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
   * Collect PLANit mode if it is known as being activated, otherwise return null
   *
   * @param gtfsMode to check for
   * @return PLANit mode
   */
  public Mode getPlanitModeIfActivated(RouteType gtfsMode){
    return modeMappingData.getPlanitModeIfActivated(gtfsMode);
  }

  public GtfsIntermodalReaderSettings getSettings() {
    return settings;
  }

  public Set<DirectedConnectoid> getConnectoidsByAccessZone(TransferZone transferZone) {
    return connectoidsByAccessZone.get(transferZone);
  }

  public ShortestPathAStar getShortestPathAlgoByMode(Mode mode) {
    return shortestPathAlgoByMode.get(mode);
  }

  public void reset() {
    this.connectoidsByAccessZone = null;
    this.shortestPathAlgoByMode = null;
  }
}
