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
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinks;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.referencing.operation.MathTransform;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Track data used during handling/parsing of GTFS Stops which end up being converted into PLANit transfer zones
 *
 * @author markr
 */
public class GtfsZoningHandlerData {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningHandlerData.class.getCanonicalName());

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

  // LOCAL DATA TRACKING - UPDATED WHILE PROCESSING

  /** track connectoid data */
  private GtfsZoningHandlerConnectoidData connectoidData;

  /** track transfer zone data */
  private GtfsZoningHandlerTransferZoneData transferZoneData;

  /** track link geospatially to identify nearby links for GTFS Stops and be able to discern if a matched transfer zone (its access link segment) is appropriate */
  private Quadtree geoIndexedLinks;

  // STATIC INFORMATION DURING PROCESSING

  /** created envelope for the rectangular bounding box of the reference network, can be used to discard unusable GTFS entities that fall outside this area */
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
  private void initialise(){
    this.serviceNodeAndModeByGtfsStopId = new HashMap<>();

    /* all link across all used layers for activated modes in geoindex format */
    Set<MacroscopicNetworkLayer> usedLayers = new HashSet<>();
    getSettings().getAcivatedPlanitModes().forEach( m -> usedLayers.add(getSettings().getReferenceNetwork().getLayerByMode(m)));
    Collection<MacroscopicLinks> linksCollection = new ArrayList<>();
    usedLayers.forEach( l -> linksCollection.add(l.getLinks()));
    this.geoIndexedLinks = GeoContainerUtils.toGeoIndexed(linksCollection);

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
            if(entry!=null && !entry.second().equals(routedService.getMode())) {
              throw new PlanItRunTimeException( "GTFS STOP %s supports multiple modes, this is not yet supported", gtfsStopId);
            }
            if(entry == null) {
              this.serviceNodeAndModeByGtfsStopId.put(gtfsStopId, Pair.of(serviceNode, routedService.getMode()));
            }
          }
        }
      }
    }

    /* extract bounding box of the reference network, used to reduce warnings in case GTFS source exceeds area covered by PLANit network */
    this.referenceNetworkBoundingBox = getSettings().getReferenceNetwork().createBoundingBox();
    if(referenceNetworkBoundingBox == null){
      LOGGER.severe("No bounding box could be created for reference network in GTFS zoning handler, likely network is empty");
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
    this.connectoidData = new GtfsZoningHandlerConnectoidData(settings.getReferenceNetwork(), zoningToPopulate);
    this.transferZoneData = new GtfsZoningHandlerTransferZoneData(settings, zoningToPopulate);

    initialise();
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
   * Get all the geo indexed links as a quad tree
   *
   * @return registered geo indexed links
   */
  public Quadtree getGeoIndexedLinks() {
    return this.geoIndexedLinks;
  }

  /** Remove link from local spatial index based on links
   *
   * @param link to remove
   */
  public void removeGeoIndexedLink(MacroscopicLink link) {
    if(link != null) {
      geoIndexedLinks.remove(link.createEnvelope(), link);
    }
  }

  /** Add provided link to local spatial index based on their bounding box
   *
   * @param link to add
   */
  public void addGeoIndexedLink(MacroscopicLink link) {
    if(link != null) {
      geoIndexedLinks.insert(link.createEnvelope(), link);
    }
  }

  /** Add provided link to local spatial index based on their bounding box
   *
   * @param links to add
   */
  public void addGeoIndexedLinks(MacroscopicLink... links) {
    if(links != null) {
      for(var link : links) {
        geoIndexedLinks.insert(link.createEnvelope(), link);
      }
    }
  }

  // CONNECTOID METHODS

  /**
   * @return bounding box of used reference network */
  public Envelope getReferenceNetworkBoundingBox() {
    return referenceNetworkBoundingBox;
  }

  /** collect the registered connectoids indexed by their locations for a given network layer (unmodifiable)
   *
   * @param networkLayer to use
   * @return registered directed connectoids indexed by location
   */
  public Map<Point, List<DirectedConnectoid>> getDirectedConnectoidsByLocation(MacroscopicNetworkLayer networkLayer) {
    return connectoidData.getDirectedConnectoidsByLocation(networkLayer);
  }

  /** Collect the registered connectoids by given locations and network layer (unmodifiable)
   *
   * @param nodeLocation to verify
   * @param networkLayer to extract from
   * @return found connectoids (if any), otherwise null or empty set
   */
  public List<DirectedConnectoid> getDirectedConnectoidsByLocation(Point nodeLocation, MacroscopicNetworkLayer networkLayer) {
    return connectoidData.getDirectedConnectoidsByLocation(nodeLocation, networkLayer);
  }

  /** Add a connectoid to the registered connectoids indexed by their OSM id
   *
   * @param networkLayer to register for
   * @param connectoidLocation this connectoid relates to
   * @param connectoid to add
   * @return true when successful, false otherwise
   */
  public boolean addDirectedConnectoidByLocation(MacroscopicNetworkLayer networkLayer, Point connectoidLocation , DirectedConnectoid connectoid) {
    return connectoidData.addDirectedConnectoidByLocation(networkLayer, connectoidLocation, connectoid);
  }

  /** Check if any connectoids have been registered for the given location on any layer
   *
   * @param location to verify
   * @return true when present, false otherwise
   */
  public boolean hasAnyDirectedConnectoidsForLocation(Point location) {
    return connectoidData.hasAnyDirectedConnectoidsForLocation(location);
  }

  /** Check if any connectoid has been registered for the given location for this layer
   *
   * @param networkLayer to check for
   * @param point to use
   * @return true when present, false otherwise
   */
  public boolean hasDirectedConnectoidForLocation(NetworkLayer networkLayer, Point point) {
    return connectoidData.hasDirectedConnectoidForLocation(networkLayer, point);
  }

  // TRANSFER ZONE METHODS

  /**
   * Register transfer as mapped to a GTFS stop, index it by its GtfsStopId, and register the stops mode as supported
   * on the PLANit transfer zone (if not already present)
   *
   * @param gtfsStop to register on PLANit transfer zone
   * @param transferZone to register one
   * @return true when already mapped by GTFS stop, false otherwise
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
   * The pt services modes supported on the given transfer zone
   *
   * @param planitTransferZone to get supported pt service modes for
   * @param modesFilter to select from
   * @return found PLANit modes
   */
  public Set<Mode> getSupportedPtModesIn(TransferZone planitTransferZone, Set<Mode> modesFilter){
    return transferZoneData.getSupportedPtModesIn(planitTransferZone, modesFilter);
  }

  /**
   * Update registered and activated pt modes and their access information on transfer zone
   *
   * @param transferZone        to update for
   * @param directedConnectoid  to extract access information from
   * @param activatedPlanitModes supported modes
   */
  public void registerTransferZoneToConnectoidModes(TransferZone transferZone, DirectedConnectoid directedConnectoid, Set<Mode> activatedPlanitModes) {
    transferZoneData.registerTransferZoneToConnectoidModes(transferZone, directedConnectoid, activatedPlanitModes);
  }

  /**
   * Connectoids related to Pt activated modes available for this transfer zone
   * @param transferZone to extract for
   * @return known connectoids
   */
  public Set<DirectedConnectoid> getTransferZoneConnectoids(TransferZone transferZone) {
    return transferZoneData.getTransferZoneConnectoids(transferZone);
  }


  /**
   * Get all the geo indexed transfer zones as a quad tree
   *
   * @return registered geo indexed transfer zones
   */
  public Quadtree getGeoIndexedPReExistingTransferZones() {
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
    return transferZoneData.createGtfsStopToTransferZoneMappingFunction();
  }
}
