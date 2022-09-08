package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Links;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.referencing.operation.MathTransform;

import java.util.*;

/**
 * Track data used during handling/parsing of GTFS Stops which end up being converted into PLANit transfer zones
 */
public class GtfsZoningHandlerData {

  // EXOGENOUS DATA TRACKING/SETTINGS

  /** settings to make available */
  private final GtfsZoningReaderSettings settings;

  /** service network to utilise */
  final ServiceNetwork serviceNetwork;

  /** routed service to utilise */
  final RoutedServices routedServices;

  /** profiler stats to update across applying of various zoning handlers that use this data instance */
  private final GtfsZoningHandlerProfiler handlerProfiler;

  /** All pre-existing service nodes and the modes this node supports by means of the routed services that visit id by their GTFS stop id.
   * Note that service nodes might reside in a layer supporting many modes, while the service node itself only covers a few routed services with a subset
   * of modes, therefore we identify those separately for better matching results when mapping service nodes/stops to GTFS STOPS here*/
  private Map<String, Pair<ServiceNode, Mode>> serviceNodeAndModeByGtfsStopId;

  // LOCAL DATA TRACKING

  /** Track all registered/mapped transfer zones by their GTFS stop id */
  private Map<String, TransferZone> mappedTransferZonesByGtfsStopId;

  /** track all GTFS stops that have been mapped to pre-existing transfer zones. We do so, to allow for correcting earlier
   * matches due to - for example - a transfer zone based on OSM was not complete and should be split in two, e.g. there are stops
   * on both sides of the road, but OSM only contains a stop on one side. In that case we must be able to retrieve the earlier mapped GTFS stop
   * and decide how to proceed
   */
  private Map<String, GtfsStop> mappedGtfsStops;

  /** track all supported pt service modes for (partly pre-existing) PLANit transfer zones that have and are to be created and
   * their used directed connectoids so we can pinpoint PT stop locations on the physical road network more accurately rather than
   * relying on the location of the transfer zone (pole, platform) which might cause mismatches compared to GTFS STOP locations */
  private Map<TransferZone,Set<DirectedConnectoid>> transferZonePtAccess;

  /** track existing transfer zones present geo spatially to be able to fuse with GTFS data when appropriate */
  private Quadtree geoIndexExistingTransferZones;

  /** track link geospatially to identify nearby links for GTFS Stops and be able to discern if a matched transfer zone (its access link segment) is appropriate */
  private Quadtree geoIndexedExistingLinks;

  /** geo tools with CRS based configuration to apply */
  private PlanitJtsCrsUtils geoTools;

  /** apply this transformation to all coordinates so they are consistent with the underlying PLANit entities */
  private MathTransform crsTransform;

  // TO POPULATE

  /** Zoning to populate (further) */
  final Zoning zoning;

  /** Update registered and activated pt modes and their access information on transfer zone
   *
   * @param transferZone to update for
   * @param directedConnectoid to extract access information from
   */
  private void registerPtAccessOnTransferZone(TransferZone transferZone, DirectedConnectoid directedConnectoid) {
    var allowedModes = ((MacroscopicLinkSegment) directedConnectoid.getAccessLinkSegment()).getAllowedModes();

    /* remove all non service modes */
    allowedModes.retainAll(getSettings().getAcivatedPlanitModes());
    if(allowedModes.isEmpty()){
      return;
    }

    /* at least one activated PT service mode present on connectoid, register it */
    transferZonePtAccess.putIfAbsent(transferZone, new HashSet<>());
    transferZonePtAccess.get(transferZone).add(directedConnectoid);
  }

  /**
   * Initialise the tracking of data
   */
  private void initialise(){
    this.mappedTransferZonesByGtfsStopId = new HashMap<>();
    this.serviceNodeAndModeByGtfsStopId = new HashMap<>();
    this.transferZonePtAccess = new HashMap<>();
    this.mappedGtfsStops = new HashMap<>();

    this.geoIndexExistingTransferZones = GeoContainerUtils.toGeoIndexed(zoning.getTransferZones());

    /* all link across all used layers for activated modes in geoindex format */
    Set<MacroscopicNetworkLayer> usedLayers = new HashSet<>();
    getSettings().getAcivatedPlanitModes().forEach( m -> usedLayers.add(getSettings().getReferenceNetwork().getLayerByMode(m)));
    Collection<Links> linksCollection = new ArrayList<>();
    usedLayers.forEach( l -> linksCollection.add(l.getLinks()));
    this.geoIndexedExistingLinks = GeoContainerUtils.toGeoIndexed(linksCollection);

    this.geoTools = new PlanitJtsCrsUtils(getSettings().getReferenceNetwork().getCoordinateReferenceSystem());
    this.crsTransform = PlanitJtsUtils.findMathTransform(PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS, geoTools.getCoordinateReferenceSystem());

    /* index: MODE -> (pre-existing) SERVICE NODE */
    for(var routedServiceLayer : getRoutedServices().getLayers()){
      for(var routedModeServices : routedServiceLayer) {
        for(var routedService : routedModeServices){
          var usedServiceNodes = routedService.getTripInfo().getScheduleBasedTrips().getUsedServiceNodes();
          usedServiceNodes.addAll(routedService.getTripInfo().getFrequencyBasedTrips().getUsedServiceNodes());
          /* mode specific service nodes */
          for(var serviceNode :  usedServiceNodes) {
            var gtfsStopId = getSettings().getServiceNodeToGtfsStopIdFunction().apply(serviceNode);
            var entry = this.serviceNodeAndModeByGtfsStopId.get(gtfsStopId);
            PlanItRunTimeException.throwIf(entry!=null && !entry.second().equals(routedService.getMode()),"GTFS STOP %s supports multiple modes, this is not yet supported", gtfsStopId);
            if(entry == null) {
              this.serviceNodeAndModeByGtfsStopId.put(gtfsStopId, Pair.of(serviceNode, routedService.getMode()));
            }
          }
        }
      }
    }

    /* index: MODE <-> (pre-existing) TRANSFER ZONE */
    if(!getZoning().getTransferConnectoids().isEmpty()){
      /* derive mode support for each transfer zone based on its connectoid (segments) modes. Used to improve matching of GTFS stops to existing
      * stops in the provided network/zoning */
      var connectoidsByAccessZone = getZoning().getTransferConnectoids().createIndexByAccessZone();
      for(var entry :connectoidsByAccessZone.entrySet()){
        if(entry.getKey() instanceof TransferZone){
          var transferZone = (TransferZone) entry.getKey();
          for(var dirConnectoid : entry.getValue()){
            /* register on transfer zone */
            registerPtAccessOnTransferZone(transferZone,dirConnectoid);
          }
        }
      }
    }

  }

  /**
   * Constructor
   *
   * @param settings to use
   * @param serviceNetwork to use
   * @param routedServices to use
   * @param handlerProfiler to use
   */
  public GtfsZoningHandlerData(final GtfsZoningReaderSettings settings, final Zoning zoningToPopulate, final ServiceNetwork serviceNetwork, final RoutedServices routedServices, final GtfsZoningHandlerProfiler handlerProfiler){
    this.zoning = zoningToPopulate;
    this.serviceNetwork = serviceNetwork;
    this.routedServices = routedServices;
    this.settings = settings;
    this.handlerProfiler = handlerProfiler;

    initialise();
  }

  /**
   * Register transfer as mapped to a GTFS stop, index it by its GtfsStopId, and register the stops mode as supported
   * on the PLANit transfer zone (if not already present)
   *
   * @param gtfsStop to register on PLANit transfer zone
   * @param transferZone to register one
   * @return true when already mapped by GTFS stop, false otherwise
   */
  public void registerMappedGtfsStop(GtfsStop gtfsStop, TransferZone transferZone) {
    var oldZone = mappedTransferZonesByGtfsStopId.put(gtfsStop.getStopId(), transferZone);
    PlanItRunTimeException.throwIf(oldZone != null && !oldZone.equals(transferZone), "Multiple transfer zones found for the same GTFS STOP_ID %s, this is not yet supported",gtfsStop.getStopId());

    var oldStop = mappedGtfsStops.put(gtfsStop.getStopId(), gtfsStop);
    PlanItRunTimeException.throwIf(oldStop != null && !oldStop.equals(gtfsStop), "Multiple GTFS stops found for the same GTFS STOP_ID %s, this is not yet supported",gtfsStop.getStopId());
  }

  /**
   * Get the transfer zone that the GTFS stop was already mapped to (if any)
   *
   * @param gtfsStop to use
   * @return PLANit transfer zone it is mapped to, null if no mapping exists yet
   */
  public TransferZone getMappedTransferZone(GtfsStop gtfsStop){
    return mappedTransferZonesByGtfsStopId.get(gtfsStop.getStopId());
  }

  /**
   * Check if transfer zone already has a mapped GTFS stop
   * @param transferZone to check
   * @return true when already mapped by GTFS stop, false otherwise
   */
  public boolean hasMappedGtfsStop(TransferZone transferZone) {
    return mappedTransferZonesByGtfsStopId.containsValue(transferZone);
  }

  /**
   * Retrieve a GTFS stop that has been mapped to a pre-existing PLANit transfer zone
   *
   * @param gtfsStopId to use
   * @return found GTFS stop (if any)
   */
  public GtfsStop getMappedGtfsStop(String gtfsStopId) {
    return mappedGtfsStops.get(gtfsStopId);
  }


  /**
   * Collect the mapped PLANit pt mode using this GTFS stop
   *
   * @param gtfsStop to collect PLANit mode for
   * @return found PLANit mode, or null if none is found
   */
  public Mode getSupportedPtMode(GtfsStop gtfsStop){
    var resultPair = this.serviceNodeAndModeByGtfsStopId.get(gtfsStop.getStopId());
    return resultPair!=null ? resultPair.second() : null;
  }

  /**
   * The pt services modes supported on the given transfer zone
   *
   * @param planitTransferZone to get supported pt service modes for
   * @param modesFilter to select from
   * @return found PLANit modes
   */
  public Set<Mode> getSupportedPtModesIn(TransferZone planitTransferZone, Set<Mode> modesFilter){
    var ptConnectoids = transferZonePtAccess.get(planitTransferZone);
    Set<Mode> ptServiceModes = new HashSet<>();
    for(var connectoid : ptConnectoids) {
      ptServiceModes.addAll(((MacroscopicLinkSegment) connectoid.getAccessLinkSegment()).getAllowedModesFrom(modesFilter));
    }
    return ptServiceModes;
  }

  /**
   * Connectoids related to Pt activated modes available for this transfer zone
   * @param transferZone to extract for
   * @return known connectoids
   */
  public Set<DirectedConnectoid> getTransferZoneConnectoids(TransferZone transferZone) {
    return transferZonePtAccess.get(transferZone);
  }

  /**
   * Access to the zoning to populate
   * @return zoning to populate (further)
   */
  public Zoning getZoning() {
    return zoning;
  }

  /** Access to the service network
   * @return the service network being populated
   */
  public ServiceNetwork getServiceNetwork() {
    return serviceNetwork;
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
  public GtfsZoningReaderSettings getSettings() {
    return this.settings;
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
   * Get all the geo indexed transfer zones as a quad tree
   *
   * @return registered geo indexed transfer zones
   */
  public Quadtree getGeoIndexedTransferZones() {
    return this.geoIndexExistingTransferZones;
  }

  /**
   * Get all the geo indexed links as a quad tree
   *
   * @return registered geo indexed links
   */
  public Quadtree getGeoIndexedExistingLinks() {
    return this.geoIndexedExistingLinks;
  }

}
