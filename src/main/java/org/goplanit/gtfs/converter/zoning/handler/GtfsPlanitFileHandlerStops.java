package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneType;
import org.locationtech.jts.geom.Point;

import java.util.Collection;
import java.util.Comparator;
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

  /** data tracking during parsing */
  private final GtfsZoningHandlerData data;

  /**
   * Find nearby zones based on a given search radius
   * @param location point location to search around (in WGS84 CRS)
   * @param pointSearchRadiusMeters search radius to apply
   * @return found transfer zones around this location (in network CRS)
   */
  private Collection<TransferZone> findNearbyTransferZones(Point location, double pointSearchRadiusMeters) {
    var searchEnvelope = data.getGeoTools().createBoundingBox(location.getX(),location.getY(),pointSearchRadiusMeters);
    searchEnvelope = PlanitJtsUtils.transformEnvelope(searchEnvelope, data.getCrsTransform());
    return GeoContainerUtils.queryZoneQuadtree(data.getGeoIndexedTransferZones(), searchEnvelope);
  }

  /**
   * Constructor
   *
   * @param data all handler related data is provided, tracked via this instance
   */
  public GtfsPlanitFileHandlerStops(GtfsZoningHandlerData data) {
    super();
    this.data = data;
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
   * @param gtfsStop      to create new TransferZone for
   */
  private void processExistingStopPlatform(final GtfsStop gtfsStop, final Collection<TransferZone> nearbyTransferZones) {
    PlanItRunTimeException.throwIfNull(gtfsStop,"GTFS stop null, this is not allowed");
    PlanItRunTimeException.throwIfNullOrEmpty(nearbyTransferZones,"No nearby transfer zones provided, this is not allowed");

    final Mode gtfsStopMode = data.getSupportedPtMode(gtfsStop);

    /* remove nearby zones that are not mode compatible */
    nearbyTransferZones.removeIf( tz -> !data.getSupportedPtModes(tz).contains(data.getSupportedPtMode(gtfsStop)));

    /* platform codes should only be present when platform is part of a station */
    if(gtfsStop.hasPlatformCode()){
      /* remove all transfer zones that are not platform like, e.g. bus poles */
      nearbyTransferZones.removeIf( tz -> tz.getTransferZoneType()==TransferZoneType.POLE);
    }

    /* find closest existing transfer zone. Note we construct envelope of transfer zone and then take its midpoint as this is
    * more representative than taking the closest node within a geometry to the gtfs stop */
    final var geoTools = data.getGeoTools();
    final var gtfsLocation = gtfsStop.getLocationAsPoint();
    final var allowCentroidGeometry = true;
    TransferZone closest = nearbyTransferZones.stream().min(
            Comparator.comparing(tz -> geoTools.getClosestDistanceInMeters(
                    gtfsLocation, tz.getGeometry(allowCentroidGeometry).getEnvelope().getCentroid()))).orElseThrow(
                      () -> new PlanItRunTimeException("Unable to locate closest transfer zone"));

    if(data.hasMappedGtfsStop(closest)){
      var splitExternalId = closest.getSplitExternalId();
      LOGGER.warning(String.format("PLANit transfer zone (%s) already mapped to GTFS stop (%s), consider mapping explicitly, creating new Transfer zone instead for STOP_ID %s",closest.getXmlId(), splitExternalId[splitExternalId.length-1],gtfsStop.getStopId()));
      processNewStopPlatform(gtfsStop);
      return;
    }

    /* augment external id with GTFS stop id + index by this id in separate map */
    if(!closest.getExternalId().contains(gtfsStop.getStopId())) {
      closest.appendExternalId(gtfsStop.getStopId());
    }

    /* update tracking data */
    data.registerMappedGtfsStop(gtfsStop, closest);

    //todo -> now either use the existing mapping to the physical network to update the reference on the service node of the stop
    //        or alternatively if there is no mapping yet create the mapping (isolate the OSM code that does this, make it generic and reuse it)

    /* profile */
    data.getProfiler().incrementAugmentedTransferZones();
  }


  private void handleStopPlatform(GtfsStop gtfsStop) {
    data.getProfiler().incrementCount(GtfsObjectType.STOP);

    Collection<TransferZone> nearbyTransferZones = findNearbyTransferZones(gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters());

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
