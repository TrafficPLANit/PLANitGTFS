package org.goplanit.gtfs.util;

import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.connectoid.TransferConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.connectoid.ZoneConnectoidType;
import org.locationtech.jts.geom.Point;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

/**
 * Utils class related to GTFS and PLANit directed connectoids functionality
 *
 * @author markr
 */
public class GtfsDirectedConnectoidHelper {

  /** no direct GTFS external id for connectoid, but signify source */
  public static final String GTFS_CONNECTOID_EXTERNAL_INFERRED_ID = "gtfs_inferred";

  /**
   * Choose the connectoid location for a GTFS stop among its candidate access segments. Every candidate is considered,
   * and the location nearest to the stop wins. When equally near, the location on the candidate with the lowest id
   * wins, so the choice does not depend on the order of the candidates.
   * <p>
   * A candidate's location that would reuse an end node of the access link which is a dead end for the access mode, see
   * {@link GtfsLinkHelper#isEndNodeReusableAsAccessNode(Node, MacroscopicLink, Mode)}, is replaced by the stop's
   * projection onto the link, where the link is then broken. A dead end is only reached in the direction running into
   * it, while the projection is reached from both sides. When the stop lies past the dead end, its projection is the
   * dead end itself, so the link cannot be broken there. Such a candidate is only used when no other candidate has a
   * location, so a location along the link wins even when it is farther. For ferries a dead-end wharf is reused as it
   * is.
   * </p>
   *
   * @param stopLocationInPlanitCrs location of the GTFS stop
   * @param candidateAccessSegments to choose from, all on the same access link
   * @param findLocationOnSegment the connectoid location on a candidate segment, null when it has none
   * @param accessMode the connectoid is for
   * @param geoUtils to measure distances and project with
   * @return the chosen location, null when no candidate has one
   */
  public static Point chooseConnectoidLocation(
      final Point stopLocationInPlanitCrs,
      final Collection<MacroscopicLinkSegment> candidateAccessSegments,
      final Function<MacroscopicLinkSegment, Point> findLocationOnSegment,
      final Mode accessMode,
      final PlanitJtsCrsUtils geoUtils){
    Point chosenLocation = null;
    double chosenDistanceMeters = Double.POSITIVE_INFINITY;
    long chosenCandidateId = Long.MAX_VALUE;
    Point deadEndLocation = null;
    double deadEndDistanceMeters = Double.POSITIVE_INFINITY;
    long deadEndCandidateId = Long.MAX_VALUE;
    for(var candidate : candidateAccessSegments){
      var location = findLocationOnSegment.apply(candidate);
      if(location == null || location.isEmpty()){
        continue;
      }
      var accessLink = candidate.getParent();
      var endNode = GtfsLinkHelper.findEndNodeWithinReachOfLocation(location, accessLink, geoUtils);
      boolean stopPastDeadEnd = false;
      if(endNode != null && !GtfsLinkHelper.isEndNodeReusableAsAccessNode(endNode, accessLink, accessMode)){
        /* a dead end: break the link at the stop instead, so both directions reach it */
        location = PlanitJtsUtils.createPoint(geoUtils.getClosestProjectedCoordinateOnLineString(
            stopLocationInPlanitCrs.getCoordinate(), accessLink.getGeometry()));
        stopPastDeadEnd = endNode.isPositionEqual2D(location.getCoordinate(), Precision.EPSILON_6);
      }

      double distanceMeters = geoUtils.getDistanceInMetres(stopLocationInPlanitCrs, location);
      if(stopPastDeadEnd){
        /* the link cannot be broken at its own end: only used when no other candidate has a location */
        if(isNearerOrEquallyNearWithLowerId(
            distanceMeters, candidate.getId(), deadEndDistanceMeters, deadEndCandidateId)){
          deadEndLocation = location;
          deadEndDistanceMeters = distanceMeters;
          deadEndCandidateId = candidate.getId();
        }
      }else if(isNearerOrEquallyNearWithLowerId(
          distanceMeters, candidate.getId(), chosenDistanceMeters, chosenCandidateId)){
        chosenLocation = location;
        chosenDistanceMeters = distanceMeters;
        chosenCandidateId = candidate.getId();
      }
    }
    return chosenLocation != null ? chosenLocation : deadEndLocation;
  }

  /**
   * Verify if a candidate location is nearer to a stop than the one chosen so far, or equally near while on a candidate
   * with a lower id
   *
   * @param distanceMeters of the candidate location to the stop
   * @param candidateId of the candidate
   * @param chosenDistanceMeters of the location chosen so far to the stop, infinite when none
   * @param chosenCandidateId of the candidate chosen so far
   * @return true when nearer, or equally near with a lower id, false otherwise
   */
  private static boolean isNearerOrEquallyNearWithLowerId(
      double distanceMeters, long candidateId, double chosenDistanceMeters, long chosenCandidateId){
    return Precision.smaller(distanceMeters, chosenDistanceMeters, Precision.EPSILON_6) ||
        (Precision.equal(distanceMeters, chosenDistanceMeters, Precision.EPSILON_6) && candidateId < chosenCandidateId);
  }

  /** create directed connectoids, one per link segment provided, all related to the given transfer zone and with
   * access modes provided. Connectoids are only created when the access link segment has at least one of the
   * allowed modes as an eligible mode.
   *
   * @param transferZone to relate connectoids to
   * @param networkLayer of the modes and link segments used
   * @param type the type restriction
   * @param accessNode the access node the connectoid utilises (determine the up/downstream connection of the attached
   *                   link segment(s)
   * @param linkSegments to create connectoids for (one per segment)
   * @param allowedModes used for each connectoid
   * @param data containing state
   * @return created connectoids (should not retun null)
   */
  public static Collection<TransferConnectoid> createAndRegisterDirectedConnectoids(
      final TransferZone transferZone,
      final MacroscopicNetworkLayer networkLayer,
      final ZoneConnectoidType type,
      final Node accessNode,
      final Iterable<? extends EdgeSegment> linkSegments,
      final Set<Mode> allowedModes, GtfsZoningHandlerData data){

    Collection<TransferConnectoid> createdConnectoids =
        ZoningConverterUtils.createAndRegisterTransferConnectoids(
            GTFS_CONNECTOID_EXTERNAL_INFERRED_ID,
            data.getZoning(),
            transferZone,
            accessNode,
            (Iterable<MacroscopicLinkSegment>) linkSegments,
            allowedModes,
            type);
    for(var newConnectoid : createdConnectoids) {
      /* update GTFS parsing specific PLANit data tracking information */

      /* 1) index by access node's location */
      data.getConverterData().getConnectoidData().addTransferConnectoidByLocation(
              networkLayer, newConnectoid.getReferenceVertex().getPosition() ,newConnectoid);
      /* 2) index connectoids on transfer zone, so we can collect it by transfer zone as well */
      data.registerTransferZoneToConnectoidModes(transferZone, type, newConnectoid, allowedModes);

    }

    return createdConnectoids;
  }
}
