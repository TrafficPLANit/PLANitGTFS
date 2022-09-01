package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServiceTripInfo;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.service.routed.RoutedTripsFrequency;
import org.goplanit.service.routed.RoutedTripsSchedule;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.service.ServiceLegSegment;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.referencing.operation.MathTransform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

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

  // PRE-EXISTING DATA TRACKING

  /** track pre-existing service nodes by their external id, which is assumed to align with the GTFS STOP ID populated during
   * parsing of the service network and routed services */
  private HashMap<String, ServiceNode> serviceNodeByExternalId;

  /** track the mapping of routed services their trips and stops (by GTFS STOP ID obtained from service node external ids) to a
   * PLANit mode so we can quickly lookup what mode a stop supports to improve the matching process */
  private HashMap<String,Mode> modeByGtfsStopId;

  // LOCAL DATA TRACKING

  /** Track all registered/mapped transfer zones by their GTFS stop id. Note that in GTFS their is no distinction between
   * where pt vehicles stop and where people transfer generally, so both service nodes and transfer zone will have a GTFS_STOP_ID
   * as (part of) their external id */
  private Map<String, TransferZone> transferZonesByGtfsStopId;

  /** track all already mapped transfer zones to be able to identify duplicate matches if needed */
  private Set<TransferZone> mappedTransferZonesToGtfsStop;

  /** track existing transfer zones present geo spatially to be able to fuse with GTFS data when appropriate */
  private Quadtree existingTransferZones;

  /** geo tools with CRS based configuration to apply */
  private PlanitJtsCrsUtils geoTools;

  /** apply this transformation to all coordinates so they are consistent with the underlying PLANit entities */
  private MathTransform crsTransform;

  // TO POPULATE

  /** Zoning to populate (further) */
  final Zoning zoning;

  /**
   * Initialise the local trackers that have no pre-existing information
   */
  private void initialiseEmptyTrackers() {
    this.transferZonesByGtfsStopId = new HashMap<>();
    this.mappedTransferZonesToGtfsStop = new HashSet<>();
  }

  /**
   * Initialise GIS based members and indices
   */
  private void initialiseGeoData() {
    this.geoTools = new PlanitJtsCrsUtils(getSettings().getReferenceNetwork().getCoordinateReferenceSystem());
    this.crsTransform = PlanitJtsUtils.findMathTransform(PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS, geoTools.getCoordinateReferenceSystem());
    this.existingTransferZones = GeoContainerUtils.toGeoIndexed(zoning.getTransferZones());
  }

  /**
   * Initialise indices from the Gtfs based service network and routed services that were populated in the GTFSServicesReader
   * to be used during parsing of transfer zones, i.e., GTFS stops here as well.
   */
  private void initialiseGtfsServicesIndices() {
    /* to be initialised indices */
    this.serviceNodeByExternalId = new HashMap<>();
    this.modeByGtfsStopId = new HashMap<>();

    /* function pair to collect from service's trip info its frequency based trips or schedule based trips */
    Pair<Function<RoutedServiceTripInfo, RoutedTripsFrequency>, Function<RoutedServiceTripInfo, RoutedTripsSchedule>> functionPair
            = Pair.of((RoutedServiceTripInfo ti) -> ti.getFrequencyBasedTrips(), (RoutedServiceTripInfo ti) -> ti.getScheduleBasedTrips());

    /* function to extract service nodes from service leg segments and place them in map by external id +
    * index the stop for the mode it supports */
    BiConsumer<ServiceLegSegment, Mode> indexLegSegmentServiceNodes = (sl, mode) -> {
      // todo -> mapping of serviceNodeByExternalId not yet used, consider removing if this remains
      serviceNodeByExternalId.put(sl.getUpstreamServiceNode().getExternalId(),sl.getUpstreamServiceNode());
      serviceNodeByExternalId.put(sl.getDownstreamServiceNode().getExternalId(),sl.getDownstreamServiceNode());
      /* GTFS STOP ID --> mode mapping (based on external id) */
      modeByGtfsStopId.put(sl.getUpstreamServiceNode().getExternalId(), mode);
      modeByGtfsStopId.put(sl.getDownstreamServiceNode().getExternalId(), mode);
    };

    /* for all layers of routed services' their scheduled and frequency based trips, extract their service nodes' external ids, i.e.,
     * their GTFS stop id's gather during the parsing of the GTFS network. */
    for(var layer :routedServices.getLayers()){
      for(var mode : layer.getSupportedModes()){
        for( var service : layer.getServicesByMode(mode)){
          /* frequency based extraction of leg segment's service nodes to index */
          functionPair.first().apply(service.getTripInfo()).forEach(
                  tf -> tf.forEach( serviceLegSegment -> indexLegSegmentServiceNodes.accept(serviceLegSegment, mode)));
          /* schedule based extraction of relative leg timing's leg segment's service nodes to index */
          functionPair.second().apply(service.getTripInfo()).forEach(
                  sf -> sf.forEach( relLegTiming -> indexLegSegmentServiceNodes.accept(relLegTiming.getParentLegSegment(), mode)));
        }
      }
    }
  }

  /**
   * Initialise the tracking of data
   */
  private void initialise(){
    initialiseEmptyTrackers();
    initialiseGeoData();
    initialiseGtfsServicesIndices();
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
   * Register transfer as mapped to a GTFS stop and index it by its GtfsStopId as well
   * @param transferZone to check
   * @return true when already mapped by GTFS stop, false otherwise
   */
  public void registerMappedGtfsStop(GtfsStop gtfsStop, TransferZone transferZone) {
    transferZonesByGtfsStopId.put(gtfsStop.getStopId(), transferZone);
    mappedTransferZonesToGtfsStop.add(transferZone);
  }

  /**
   * Check if transfer zone already has a mapped GTFS stop
   * @param transferZone to check
   * @return true when already mapped by GTFS stop, false otherwise
   */
  public boolean hasMappedGtfsStop(TransferZone transferZone) {
    return mappedTransferZonesToGtfsStop.contains(transferZone);
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
   * Get all the geo index transfer zones as a quad tree
   *
   * @return registered geo indexed transfer zones
   */
  public Quadtree getGeoIndexedTransferZones() {
    return this.existingTransferZones;
  }

  /**
   * Mode that goes with the given GTFS STOP ID (if any)
   *
   * @param gtfsStopId to collect for
   * @return found PLANit mode
   */
  public Mode getGtfsStopMode(String gtfsStopId) {
    return this.modeByGtfsStopId.get(gtfsStopId);
  }
}
