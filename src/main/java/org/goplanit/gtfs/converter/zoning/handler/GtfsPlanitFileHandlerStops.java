package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneType;
import org.goplanit.utils.zoning.Zone;
import org.locationtech.jts.geom.Point;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
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
   * @param gtfsStop to create new TransferZone for
   */
  private void processExistingStopPlatform(final GtfsStop gtfsStop, final Collection<TransferZone> nearbyTransferZones) {
    PlanItRunTimeException.throwIfNull(gtfsStop,"GTFS stop null, this is not allowed");
    PlanItRunTimeException.throwIfNullOrEmpty(nearbyTransferZones,"No nearby transfer zones provided, this is not allowed");

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
      LOGGER.warning(String.format("PLANit transfer zone (%s) already mapped to GTFS stop, consider mapping explicitly, creating new Transfer zone instead for STOP_ID %s",closest.getXmlId(), gtfsStop.getStopId()));
      processNewStopPlatform(gtfsStop);
      return;
    }

    /* augment external id with GTFS stop id + index by this id in separate map */
    if(!closest.getExternalId().contains(gtfsStop.getStopId())) {
      closest.appendExternalId(gtfsStop.getStopId());
    }

    /* update tracking data */
    data.registerMappedGtfsStop(gtfsStop, closest);

    /* profile */
    data.getProfiler().incrementAugmentedTransferZones();
  }


  /**
   * Process GTFS stop which is a platform and convert it to a PLANit transfer zone given it has a valid and mapped PLANit mode
   *
   * @param gtfsStop to handle
   */
  private void handleStopPlatform(GtfsStop gtfsStop) {
    data.getProfiler().incrementCount(GtfsObjectType.STOP);

    /* mapped PLANit mode available */
    Mode mode = data.getGtfsStopMode(gtfsStop.getStopId());
    if(mode == null){
      return;
    }

    /* spatially select possible existing transfer zone matches */
    Collection<TransferZone> nearbyTransferZones = findNearbyTransferZones(gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters());
    /* only retain transfer zones supporting the stop's mode */

    //todo -> implement this on directed connectoids implementation -> then move this to data to avoid redoing it continuously
    //        then use it to identify the connectoids for the zones and only retain those with at least one connectoids supporting the mode
    Map<Zone, Set<DirectedConnectoid>> indexByTransferZone = data.getZoning().getTransferConnectoids().createIndexByAccessZone();

    nearbyTransferZones.removeIf( tz -> !data.getZoning().getTransferConnectoids().getFirst().getExplicitlyAllowedModes(tz).contains(mode));

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
