package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.referencing.operation.MathTransform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

  // LOCAL DATA TRACKING

  /** Track all registered/mapped transfer zones by their GTFS stop id */
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
   * Initialise the tracking of data
   */
  private void initialise(){
    this.transferZonesByGtfsStopId = new HashMap<>();
    this.mappedTransferZonesToGtfsStop = new HashSet<>();

    this.existingTransferZones = GeoContainerUtils.toGeoIndexed(zoning.getTransferZones());
    this.geoTools = new PlanitJtsCrsUtils(getSettings().getReferenceNetwork().getCoordinateReferenceSystem());
    this.crsTransform = PlanitJtsUtils.findMathTransform(PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS, geoTools.getCoordinateReferenceSystem());
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
}
