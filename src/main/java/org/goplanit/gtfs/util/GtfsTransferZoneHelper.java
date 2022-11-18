package org.goplanit.gtfs.util;

import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.CharacterUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

import java.util.Collection;
import java.util.logging.Logger;

import static org.goplanit.utils.locale.DrivingDirectionDefaultByCountry.isLeftHandDrive;

/**
 * Utils class related to GTFS and PLANit transfer zone functionality
 *
 * @author markr
 */
public class GtfsTransferZoneHelper {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsTransferZoneHelper.class.getCanonicalName());

  /**
   * Helper method to create and register a new transfer zone based on a GTFS stop
   *
   * @param gtfsStop to use
   * @param projectedGtfsStopLocation projected location to use
   * @param type type of transfer zone
   * @param data containing state
   * @return created and registered transfer zone
   */
  public static TransferZone createAndRegisterNewTransferZone(GtfsStop gtfsStop, Point projectedGtfsStopLocation, TransferZoneType type, GtfsZoningHandlerData data) {
    TransferZone transferZone = data.getZoning().getTransferZones().getFactory().registerNew(type, true);
    transferZone.setGeometry(projectedGtfsStopLocation);

    /* external id  = GTFS stop id*/
    transferZone.setExternalId(gtfsStop.getStopId());

    /* name */
    transferZone.setName(gtfsStop.getStopName());

    /* platform name */
    transferZone.addTransferZonePlatformName(gtfsStop.getPlatformCode());

    data.getProfiler().incrementCreatedTransferZones();

    return  transferZone;
  }

  /** extract last entry from Transfer zone external id based on comma separation
   * @param transferZone to use
   * @return last entry or empty string
   */
  public static String getLastTransferZoneExternalId(TransferZone transferZone){
    var splitExternalId = transferZone.getSplitExternalId(CharacterUtils.COMMA);
    return splitExternalId==null ? "" : splitExternalId[splitExternalId.length-1];
  }

  /**
   * Verify based on driving direction and orientation of the access link segment(s) whether the GTFS stop is a viable match for the
   * found transfer zone in terms of being on the correct side of the road. The assumption here is that this pertains to a road based stop
   * not rail and connectoids being available for the provided transfer zone to extract this information
   *
   * @param gtfsStop to verify
   * @param transferZone to verify against
   * @param data containing state
   * @return true when on correct side of the road, false otherwise
   */
  public static boolean isGtfsStopOnCorrectSideOfTransferZoneAccessLinkSegments(GtfsStop gtfsStop, TransferZone transferZone, GtfsZoningHandlerData data) {
    boolean leftHandDrive = isLeftHandDrive(data.getSettings().getCountryName());
    var connectoids = data.getTransferZoneConnectoids(transferZone);
    if(connectoids== null || connectoids.isEmpty()){
      LOGGER.warning(String.format("Cannot determine of GTFS stop (%s) is on correct side of transfer zone (%s) access links since transfer zone has no connectoids associated with it, this shouldn't happen", gtfsStop.getStopId(), transferZone.getXmlId()));
      return false;
    }

    for (var connectoid : connectoids) {
      var accessSegment = connectoid.getAccessLinkSegment();
      var localProjection = PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());
      if(localProjection==null){
        return false;
      }
      if(!GtfsLinkSegmentHelper.isGeometryOnCorrectSideOfLinkSegment(localProjection, accessSegment, leftHandDrive, data.getGeoTools())){
        /* not compatible for one of its access link segments, */
        return false;
      }
    }
    return true;
  }

  /**
   * find the transfer zone (underlying stop locations if any) closest to the provided GTFS stop location. In case the transfer zone has no
   * stop locations registered, we use its overall geometry to match the distance to the GTFS stop location.
   *
   * @param gtfsStopLocation to find closest transfer zone for
   * @param nearbyTransferZones to consider
   * @param data containing state
   * @return found closest transfer zone
   */
  public static Pair<TransferZone,Double> findTransferZoneStopLocationClosestTo(Coordinate gtfsStopLocation, Collection<TransferZone> nearbyTransferZones, GtfsZoningHandlerData data) {
    TransferZone closest = nearbyTransferZones.iterator().next();
    if(nearbyTransferZones.size()==1) {
      return Pair.of(closest, PlanitEntityGeoUtils.getDistanceToZone(gtfsStopLocation, closest, data.getGeoTools()));
    }

    /* multiple options -> use transfer zone geometry or underlying connectoid access nodes (stop locations) */
    double minDistance = Double.POSITIVE_INFINITY;
    final var allowCentroidGeometry = true;
    for(var transferZone : nearbyTransferZones) {
      var directedConnectoids = data.getTransferZoneConnectoids(transferZone);

      /* transfer zone geometry based */
      if (directedConnectoids == null || directedConnectoids.isEmpty()) {
        var planitTransferZoneStopLocation = transferZone.getGeometry(allowCentroidGeometry).getCentroid().getCoordinate();
        double distance = data.getGeoTools().getDistanceInMetres(gtfsStopLocation, planitTransferZoneStopLocation);
        if (minDistance > distance) {
          closest = transferZone;
          minDistance = distance;
        }
      } else {
        /* connectoid access node based */
        for (var dirConnectoid : directedConnectoids) {
          var planitTransferZoneStopLocation = dirConnectoid.getAccessNode().getPosition().getCoordinate();
          double distance = data.getGeoTools().getDistanceInMetres(gtfsStopLocation, planitTransferZoneStopLocation);
          if (minDistance > distance) {
            closest = transferZone;
            minDistance = distance;
          }
        }
      }
    }
    return Pair.of(closest,minDistance);
  }

  /**
   * Find nearby zones based on a given search radius
   * @param location point location to search around (in WGS84 CRS)
   * @param pointSearchRadiusMeters search radius to apply
   * @param data containing state
   * @return found transfer zones around this location (in network CRS)
   */
  public static Collection<TransferZone> findNearbyTransferZones(Point location, double pointSearchRadiusMeters, GtfsZoningHandlerData data) {
    //todo change implementation so it does not necessarily require WGS84 input locations as it is inconsistent with the utils class
    var searchEnvelope = data.getGeoTools().createBoundingBox(location.getX(),location.getY(),pointSearchRadiusMeters);
    searchEnvelope = PlanitJtsUtils.transformEnvelope(searchEnvelope, data.getCrsTransform());
    return GeoContainerUtils.queryZoneQuadtree(data.getGeoIndexedPReExistingTransferZones(), searchEnvelope);
  }
}
