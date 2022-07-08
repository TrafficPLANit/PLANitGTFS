package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.zoning.GtfsPublicTransportReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandler;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneType;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.referencing.operation.MathTransform;

import java.util.*;
import java.util.logging.Logger;

/**
 * Handler for handling stops and augmenting a PLANit zoning with the found stops in the process
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerStops extends GtfsFileHandlerStops {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerStops.class.getCanonicalName());

  /** profiler to use */
  private final GtfsZoningHandlerProfiler profiler;

  /** Track all registered/mapped transfer zones by their GTFS stop id */
  private Map<String, TransferZone> transferZonesByGtfsStopId;

  /** track all already mapped transfer zones to be able to identify duplicate matches if needed */
  private Set<TransferZone> mappedTransferZones;

  /** track existing transfer zones present geo spatially to be able to fuse with GTFS data when appropriate */
  private Quadtree existingTransferZones;

  /** geo tools with CRS based configuration to apply */
  private PlanitJtsCrsUtils geoTools;

  /** apply this transformation to all coordinates so they are consistent with the underlying PLANit entities */
  private MathTransform crsTransform;

  /** zoning to populate */
  private final Zoning zoning;

  /** settings containing configuration */
  private final GtfsPublicTransportReaderSettings settings;

  /**
   * Initialise this handler
   */
  private void initialise(){
    this.transferZonesByGtfsStopId = new HashMap<>();
    this.mappedTransferZones = new HashSet<>();

    this.existingTransferZones = GeoContainerUtils.toGeoIndexed(zoning.getTransferZones());
    this.geoTools = new PlanitJtsCrsUtils(settings.getReferenceNetwork().getCoordinateReferenceSystem());
    this.crsTransform = PlanitJtsUtils.findMathTransform(PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS, geoTools.getCoordinateReferenceSystem());
  }

  /**
   * Find nearby zones based on a given search radius
   * @param location point location to search around (in WGS84 CRS)
   * @param pointSearchRadiusMeters search radius to apply
   * @return found transfer zones around this location (in network CRS)
   */
  private Collection<TransferZone> findNearbyTransferZones(Point location, double pointSearchRadiusMeters) {
    var searchEnvelope = geoTools.createBoundingBox(location.getX(),location.getY(),pointSearchRadiusMeters);
    searchEnvelope = PlanitJtsUtils.transformEnvelope(searchEnvelope, this.crsTransform);
    return GeoContainerUtils.queryZoneQuadtree(this.existingTransferZones, searchEnvelope);
  }

  /**
   * Check if transfer zone already has a mapped GTFS stop
   * @param transferZone to check
   * @return true when already mapped by GTFS stop, false otherwise
   */
  private boolean hasMappedGtfsStop(TransferZone transferZone) {
    return mappedTransferZones.contains(transferZone);
  }

  /**
   * Register transfer as mapped to a GTFS stop and index it by its GtfsStopId as well
   * @param transferZone to check
   * @return true when already mapped by GTFS stop, false otherwise
   */
  private void registerMappedGtfsStop(GtfsStop gtfsStop, TransferZone transferZone) {
    transferZonesByGtfsStopId.put(gtfsStop.getStopId(), transferZone);
    mappedTransferZones.add(transferZone);
  }

  /**
   * Constructor
   *
   * @param zoningToPopulate the PLANit zoning instance to populate (further)
   * @param settings to apply where needed
   * @param profiler to use
   */
  public GtfsPlanitFileHandlerStops(final Zoning zoningToPopulate, final GtfsPublicTransportReaderSettings settings, final GtfsZoningHandlerProfiler profiler) {
    super();
    this.profiler = profiler;
    this.zoning = zoningToPopulate;
    this.settings = settings;
    initialise();
  }

  /**
   * Process a GTFS stop that could not be matched to an existing transfer zone. It will trigger the
   * creation of a new transfer zone on the PLANit zoning
   *
   * @param gtfsStop to create new TransferZone for
   */
  private void processNewStopPlatform(GtfsStop gtfsStop) {
  }

  /**
   * Process a GTFS stop that could be matched to nearby existing transfer zone(s). It will trigger the
   * augmenting the most suitable existing transfer zone with the GTFS stop information
   *
   * @param gtfsStop to create new TransferZone for
   */
  private void processExistingStopPlatform(final GtfsStop gtfsStop, final Collection<TransferZone> nearbyTransferZones) {
    PlanItRunTimeException.throwIfNull(gtfsStop,"GTFS stop null, this is not allowed");
    PlanItRunTimeException.throwIfNullOrEmpty(nearbyTransferZones,"No nearby transfer zones provided, this is not allowed");


    //TODO: GTFS-stops implicit mode support cannot be obtained from the GtfsStop itself, it is obtained from the
    //      routes passing by. However, it is needed in order to properly match the the stops to transfer zones because
    //      transfer zones may reside on top of one another (Wynyard) and we lack information on platform references to do this
    //      by name alone. Proposal: First do a pass where we collect the
    //
    //      To obtain the modes for transfer zone we need to look at the connectoids. Currently these are
    //      not accessible from the transfer zones themselves. Proposal: create transport network that should create the
    //      mapping from zoning to network, or alternatively do some pre-processing as connectoids od have references to the
    //      transfer zones.

    if(gtfsStop.hasPlatformCode()){
      /* remove all transfer zones that are not platform like, e.g. bus poles */
      nearbyTransferZones.removeIf( tz -> tz.getTransferZoneType()==TransferZoneType.POLE);
    }

    /* find closest existing transfer zone. Note we construct envelope of transfer zone and then take its midpoint as this is
    * more representative than taking the closest node within a geometry to the gtfs stop */
    final var gtfsLocation = gtfsStop.getLocationAsPoint();
    final var allowCentroidGeometry = true;
    TransferZone closest = nearbyTransferZones.stream().min(
            Comparator.comparing(tz -> geoTools.getClosestDistanceInMeters(
                    gtfsLocation, tz.getGeometry(allowCentroidGeometry).getEnvelope().getCentroid()))).orElseThrow(
                      () -> new PlanItRunTimeException("Unable to locate closest transfer zone"));

    if(hasMappedGtfsStop(closest)){
      LOGGER.warning(String.format("PLANit transfer zone (%s) already mapped to GTFS stop, consider mapping explicitly, creating new Transfer zone instead for STOP_ID %s",closest.getXmlId(), gtfsStop.getStopId()));
      processNewStopPlatform(gtfsStop);
      return;
    }

    /* augment external id with GTFS stop id + index by this id in separate map */
    if(!closest.getExternalId().contains(gtfsStop.getStopId())) {
      closest.appendExternalId(gtfsStop.getStopId());
    }

    /* update tracking data */
    registerMappedGtfsStop(gtfsStop, closest);

    /* profile */
    profiler.incrementAugmentedTransferZones();
  }


  private void handleStopPlatform(GtfsStop gtfsStop) {
    profiler.incrementCount(GtfsObjectType.STOP);

    Collection<TransferZone> nearbyTransferZones = findNearbyTransferZones(gtfsStop.getLocationAsPoint(), settings.getGtfsStopToTransferZoneSearchRadiusMeters());
    if(nearbyTransferZones.isEmpty()){
       processNewStopPlatform(gtfsStop);
       return;
    }
    processExistingStopPlatform(gtfsStop, nearbyTransferZones);
  }

  /**
   * Handle a GTFS stop
   */
  @Override
  public void handle(GtfsStop gtfsStop) {
    switch (gtfsStop.getLocationType()){
      case STOP_PLATFORM:
        handleStopPlatform(gtfsStop);
      case BOARDING_AREA:
        // not processed yet
        return;
      case STATION:
        // not processed yet
        return;
      case GENERIC_NODE:
        // not processed yet
        return;
      case ENTRANCE_EXIT:
        // not processed yet
        return;
      default:
        throw new PlanItRunTimeException("Unrecognised GTFS stop location type %s encountered", gtfsStop.getLocationType());
    }
  }

}
