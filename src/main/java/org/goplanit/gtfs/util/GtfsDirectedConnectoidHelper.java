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
import java.util.HashSet;
import java.util.List;
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
   * Choose the connectoid location for a GTFS stop among its candidate access segments. Every candidate is considered:
   * the nearest location reusing its candidate's end node wins, so the link is not broken, otherwise the location
   * nearest to the stop. When equally near, the location on the candidate with the lowest id wins, so the choice does
   * not depend on the order of the candidates, see
   * {@link ZoningConverterUtils#chooseVehicleStopConnectoidLocationOnAccessLink}.
   * <p>
   * A candidate's location that would reuse an end node of the access link which is a dead end for the access mode, see
   * {@link GtfsLinkHelper#isEndNodeReusableAsAccessNode(Node, MacroscopicLink, Mode)}, is replaced by the stop's
   * projection onto the link, where the link is then broken. A dead end is only reached in the direction running into
   * it, while the projection is reached from both sides. A location along the link near a dead end is kept, both
   * directions reach it already. When the stop lies past the dead end, its projection is the dead end itself, so the
   * link cannot be broken there. A candidate whose location then is within the given distance of the dead end, the dead
   * end itself included, is only used when no other candidate has a location, so a location farther along the link
   * wins even when it is farther from the stop. For ferries a dead-end wharf is reused as it is.
   * </p>
   *
   * @param stopLocationInPlanitCrs location of the GTFS stop
   * @param candidateAccessSegments to choose from, all on the same access link
   * @param findLocationOnSegment the connectoid location on a candidate segment, null when it has none
   * @param accessMode the connectoid is for
   * @param maxLocationToDeadEndMeters distance from a dead end the stop lies past within which a location counts as
   *                                   the dead end
   * @param geoUtils to measure distances and project with
   * @return the chosen location, null when no candidate has one
   */
  public static Point chooseVehicleStopConnectoidLocation(
      final Point stopLocationInPlanitCrs,
      final Collection<MacroscopicLinkSegment> candidateAccessSegments,
      final Function<MacroscopicLinkSegment, Point> findLocationOnSegment,
      final Mode accessMode,
      final double maxLocationToDeadEndMeters,
      final PlanitJtsCrsUtils geoUtils){
    /* candidates whose stop lies past a dead end, noted as their location is found, so that location is a last resort */
    var candidatesPastDeadEnd = new HashSet<MacroscopicLinkSegment>();
    return ZoningConverterUtils.chooseVehicleStopConnectoidLocationOnAccessLink(
        candidateAccessSegments,
        candidate -> {
          var location = findLocationOnSegment.apply(candidate);
          if(location == null || location.isEmpty()){
            return location;
          }
          var accessLink = candidate.getParent();
          for(var endNode : List.<Node>of(accessLink.getNodeB(), accessLink.getNodeA())) {
            if(GtfsLinkHelper.isEndNodeReusableAsAccessNode(endNode, accessLink, accessMode) ||
                !geoUtils.isDistanceWithinMetres(endNode.getPosition(), location, maxLocationToDeadEndMeters)) {
              continue;
            }
            var projection = PlanitJtsUtils.createPoint(geoUtils.getClosestProjectedCoordinateOnLineString(
                stopLocationInPlanitCrs.getCoordinate(), accessLink.getGeometry()));
            if(endNode.isPositionEqual2D(location.getCoordinate(), Precision.EPSILON_6)){
              /* the dead end itself: break the link at the stop instead, so both directions reach it */
              location = projection;
            }
            if(endNode.isPositionEqual2D(projection.getCoordinate(), Precision.EPSILON_6)){
              /* the stop lies past the dead end: only used when no other candidate has a location */
              candidatesPastDeadEnd.add(candidate);
            }
          }
          return location;
        },
        location -> geoUtils.getDistanceInMetres(stopLocationInPlanitCrs, location),
        (candidate, location) -> candidatesPastDeadEnd.contains(candidate));
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
