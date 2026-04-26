package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.converter.zoning.ZoningConverterCommonData;
import org.goplanit.gtfs.converter.GtfsConverterModeMappingData;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinks;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.zoning.TransferConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.ZoneConnectoidType;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.geotools.api.referencing.operation.MathTransform;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Track data used during handling/parsing of GTFS Stops which end up being converted into PLANit transfer zones
 *
 * @author markr
 */
public class GtfsZoningHandlerData extends GtfsConverterModeMappingData {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningHandlerData.class.getCanonicalName());

  // EXOGENOUS DATA TRACKING/SETTINGS

  /** routed service to utilise */
  final RoutedServices routedServices;

  /** profiler stats to update across applying of various zoning handlers that use this data instance */
  private final GtfsZoningHandlerProfiler handlerProfiler;

  /** All pre-existing service nodes and the modes this node supports by means of the routed services that visit id
   * by their GTFS stop id. Note that service nodes might reside in a layer supporting many modes, while the
   * service node itself only covers a few routed services with a subset of modes, therefore we identify
   * those separately for better matching results when mapping service nodes/stops to GTFS STOPS here*/
  private Map<String, Pair<ServiceNode, List<Mode>>> serviceNodeModesByGtfsStopId;

  // LOCAL DATA TRACKING - UPDATED WHILE PROCESSING

  /** track spatially indexed links and connectoids by location and other common data using the PLANit core
   * functionality out of the box */
  ZoningConverterCommonData commonConverterData;

  /** track transfer zone data */
  private GtfsZoningHandlerTransferZoneData transferZoneData;

  // STATIC INFORMATION DURING PROCESSING

  /** created envelope for the rectangular bounding box of the reference network, can be used to discard unusable
   * GTFS entities that fall outside this area */
  private Envelope referenceNetworkBoundingBox;

  /** geo tools with CRS based configuration to apply */
  private PlanitJtsCrsUtils geoTools;

  /** apply this transformation to all coordinates so they are consistent with the underlying PLANit entities */
  private MathTransform crsTransform;

  // TO POPULATE

  /** Zoning to populate (further) */
  final Zoning zoning;

  /**
   * Initialise the tracking of data
   */
  protected void initialise(){
    this.serviceNodeModesByGtfsStopId = new HashMap<>();

    var connectoidData = new GtfsZoningHandlerConnectoidData(getServiceNetwork(), getZoning());
    this.commonConverterData = new ZoningConverterCommonData(
        getServiceNetwork().getParentNetwork(), getZoning(), connectoidData);
    /* all links across all used layers for activated modes in geoindexed format */
    commonConverterData.recreateSpatiallyIndexedLinks();

    this.geoTools = new PlanitJtsCrsUtils(getServiceNetwork().getParentNetwork().getCoordinateReferenceSystem());
    this.crsTransform = PlanitJtsUtils.findMathTransform(
        PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS, geoTools.getCoordinateReferenceSystem());

    /* index: MODE -> (pre-existing) SERVICE NODE */
    for(var routedServiceLayer : getRoutedServices().getLayers()){
      for(var routedModeServices : routedServiceLayer) {
        for(var routedService : routedModeServices){
          if(!routedService.getTripInfo().hasAnyTrips()){
            LOGGER.warning(String.format("Found empty routed service %s %s, indicating sub-optimal or " +
                "corrupt PLANit routed services, this shouldn't happen",
                routedService.getXmlId(), routedService.getName()));
            continue;
          }

          var usedServiceNodes = routedService.getTripInfo().getScheduleBasedTrips().determineUsedServiceNodes();
          usedServiceNodes.addAll(routedService.getTripInfo().getFrequencyBasedTrips().determineUsedServiceNodes());
          /* mode specific service nodes */
          for(var serviceNode :  usedServiceNodes) {
            var gtfsStopId = getSettings().getServiceNodeToGtfsStopIdFunction().apply(serviceNode);
            var entry = this.serviceNodeModesByGtfsStopId.get(gtfsStopId);
            if(entry == null) {
              entry = Pair.of(serviceNode, new ArrayList<>(1));
              this.serviceNodeModesByGtfsStopId.put(gtfsStopId, entry);
            }
            var supportedModes = entry.second();
            if(!supportedModes.contains(routedService.getMode())){
              supportedModes.add(routedService.getMode());
            }
          }
        }
      }
    }

    /* extract bounding box of the reference network, used to reduce warnings in case GTFS source exceeds
    area covered by PLANit network */
    this.referenceNetworkBoundingBox = getServiceNetwork().getParentNetwork().createBoundingBox();
    if(referenceNetworkBoundingBox == null){
      LOGGER.severe("No bounding box could be created for reference network in GTFS zoning handler, " +
          "likely network is empty");
    }

  }

  /**
   * Constructor
   *
   * @param settings to use
   * @param zoningToPopulate the zoning to populate
   * @param serviceNetwork to use
   * @param routedServices to use
   * @param handlerProfiler to use
   */
  public GtfsZoningHandlerData(
      final GtfsZoningReaderSettings settings,
      final Zoning zoningToPopulate,
      final ServiceNetwork serviceNetwork,
      final RoutedServices routedServices,
      final GtfsZoningHandlerProfiler handlerProfiler){
    super(serviceNetwork, settings);
    this.zoning = zoningToPopulate;
    this.routedServices = routedServices;
    this.handlerProfiler = handlerProfiler;

    initialise();
    this.transferZoneData = new GtfsZoningHandlerTransferZoneData(serviceNetwork, settings, zoningToPopulate);
  }

  /**
   * Collect the mapped PLANit pt mode using this GTFS stop
   *
   * @param gtfsStop to collect PLANit mode for
   * @return found PLANit modes, or null if none is found
   */
  public List<Mode> getSupportedPtModes(GtfsStop gtfsStop){
    var resultPair = this.serviceNodeModesByGtfsStopId.get(gtfsStop.getStopId());
    return resultPair!=null ? resultPair.second() : null;
  }

  /**
   * Access to the zoning to populate
   * @return zoning to populate (further)
   */
  public Zoning getZoning() {
    return zoning;
  }

  /** Access to the routed services container
   * @return the routed services  being populated
   */
  public RoutedServices getRoutedServices(){
    return this.routedServices;
  }

  /**
   * Access to profiler
   *
   * @return profiler
   */
  public GtfsZoningHandlerProfiler getProfiler() {
    return handlerProfiler;
  }

  /**
   * Access to GTFS zoning reader settings
   *
   * @return user configuration settings
   */
  @Override
  public GtfsZoningReaderSettings getSettings() {
    return (GtfsZoningReaderSettings) super.getSettings();
  }

  /**
   * Get geo tools to provide PLANit related GIS functionality
   *
   * @return geo tools
   */
  public PlanitJtsCrsUtils getGeoTools(){
    return this.geoTools;
  }

  /**
   * Get Math transform to apply on the fly transformations for the CRS at hand
   *
   * @return transformation
   */
  public MathTransform getCrsTransform() {
    return this.crsTransform;
  }

    /**
   * @return bounding box of used reference network */
  public Envelope getReferenceNetworkBoundingBox() {
    return referenceNetworkBoundingBox;
  }

  /**
   * Access to common converter tracking data
   *
   * @return instance
   */
  public ZoningConverterCommonData getConverterData(){
    return commonConverterData;
  }

  // TRANSFER ZONE METHODS

  /**
   * Register transfer as mapped to a GTFS stop, index it by its GtfsStopId, and register the stops mode as supported
   * on the PLANit transfer zone (if not already present)
   *
   * @param gtfsStop to register on PLANit transfer zone
   * @param transferZone to register one
   */
  public void registerMappedGtfsStop(GtfsStop gtfsStop, TransferZone transferZone) {
    transferZoneData.registerMappedGtfsStop(gtfsStop, transferZone);
  }

  /**
   * Get the transfer zone that the GTFS stop was already mapped to (if any)
   *
   * @param gtfsStop to use
   * @return PLANit transfer zone it is mapped to, null if no mapping exists yet
   */
  public TransferZone getMappedTransferZone(GtfsStop gtfsStop){
    return transferZoneData.getMappedTransferZone(gtfsStop);
  }

  /**
   * Check if transfer zone already has a mapped GTFS stop
   * @param transferZone to check
   * @return true when already mapped by GTFS stop, false otherwise
   */
  public boolean hasMappedGtfsStop(TransferZone transferZone) {
    return transferZoneData.hasMappedGtfsStop(transferZone);
  }

  /**
   * Retrieve a GTFS stop that has been mapped to a pre-existing PLANit transfer zone
   *
   * @param gtfsStopId to use
   * @return found GTFS stop (if any)
   */
  public GtfsStop getMappedGtfsStop(String gtfsStopId) {
    return transferZoneData.getMappedGtfsStop(gtfsStopId);
  }

  /**
   * The pt services modes supported on the given transfer zone with entries of type PT_VEHICLE_STOP
   *
   * @param planitTransferZone to get supported pt service modes for
   * @param modesFilter to select from
   * @return found PLANit modes
   */
  public Set<Mode> getSupportedPtModesIn(
      TransferZone planitTransferZone, Set<Mode> modesFilter){
    return transferZoneData.getSupportedPtModesIn(planitTransferZone, modesFilter);
  }

  /**
   * Update registered and activated pt modes and their access information on transfer zone
   *
   * @param transferZone        to update for
   * @param type the type restriction
   * @param directedConnectoid  to extract access information from
   * @param activatedPlanitModes supported modes
   */
  public void registerTransferZoneToConnectoidModes(
      TransferZone transferZone,
      ZoneConnectoidType type,
      TransferConnectoid directedConnectoid,
      Set<Mode> activatedPlanitModes) {
    activatedPlanitModes.forEach(
        m -> registerTransferZoneToConnectoidMode(transferZone, type, directedConnectoid, m));
  }
  /**
   * Update registered and activated mode and their access information on transfer zone
   *
   * @param transferZone        to update for
   * @param type the type restriction
   * @param directedConnectoid  to extract access information from
   * @param activatedPlanitMode supported modes
   */
  public void registerTransferZoneToConnectoidMode(
      TransferZone transferZone,
      ZoneConnectoidType type,
      TransferConnectoid directedConnectoid,
      Mode activatedPlanitMode) {
    transferZoneData.registerTransferZoneToConnectoidMode(transferZone, type, directedConnectoid, activatedPlanitMode);
  }

  /**
   * Connectoids related to Pt activated modes available for this transfer zone
   * @param transferZone to extract for
   * @return known connectoids
   */
  public Set<TransferConnectoid> getTransferZoneConnectoids(TransferZone transferZone) {
    return transferZoneData.getTransferZoneConnectoids(transferZone);
  }


  /**
   * Get all the geo indexed transfer zones as a quad tree
   *
   * @return registered geo indexed transfer zones
   */
  public Quadtree getGeoIndexedPreExistingTransferZones() {
    return transferZoneData.getGeoIndexedPreExistingTransferZones();
  }

  /**
   * Get all the existing transfer zones by their external id
   *
   * @return existing transfer zones by external id
   */
  public Map<String, TransferZone> getPreExistingTransferZonesByExternalId() {
    return transferZoneData.getPreExistingTransferZonesByExternalId();
  }

  /**
   * Create mapping function while hiding how the mapping is stored
   *
   * @return function that can map GTFS stop ids to transfer zones based on internal state of this data tracker
   */
  public Function<String, TransferZone> createGtfsStopToTransferZoneMappingFunction() {
    return transferZoneData.createGtfsStopToTransferZonesMappingFunction();
  }

}
