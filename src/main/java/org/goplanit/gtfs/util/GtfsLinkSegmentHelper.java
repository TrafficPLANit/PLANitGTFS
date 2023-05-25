package org.goplanit.gtfs.util;

import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;

/**
 * Utils class related to GTFS and PLANit link segment functionality
 *
 * @author markr
 */
public class GtfsLinkSegmentHelper {

  /**
   *  check if geometry is on correct side of link segment by taking the link segment's extreme nodes as reference point. For long
   *  links this is probably not a good approach.
   *
   * @param geometry to check
   * @param linkSegment to check
   * @param shouldBeOnLeft flag indicating whether it should be on left, if false, it should be on the right
   * @param geoTools to use
   * @return true when correct, false otherwise
   */
  public static boolean isGeometryOnCorrectSideOfLinkSegment(Geometry geometry, LinkSegment linkSegment, boolean shouldBeOnLeft, PlanitJtsCrsUtils geoTools) {
    LineSegment finalLineSegment = PlanitEntityGeoUtils.extractClosestLineSegmentToGeometryFromLinkSegment(geometry, linkSegment, geoTools);
    /* determine location relative to infrastructure */
    return shouldBeOnLeft == geoTools.isGeometryLeftOf(geometry, finalLineSegment.p0, finalLineSegment.p1);
  }

  /** Based on coordinate draw a virtual line to closest intersection point on link segment and identify the azimuth (0-360 degrees) that goes with this virtual line
   *  from the intersection point on the link segment towards the coordinate provided
   *
   * @param linkSegment to use
   * @param coordinate to use
   * @param data containing state
   * @return azimuth in degrees found
   */
  public static double getAzimuthFromLinkSegmentToCoordinate(LinkSegment linkSegment, Coordinate coordinate, GtfsZoningHandlerData data) {
    PlanItRunTimeException.throwIfNull(linkSegment, "LinkSegment is null");
    PlanItRunTimeException.throwIfNull(coordinate, "Coordinate is null");

    var linkSegmentGeometry = linkSegment.getParent().getGeometry();
    var closestLinkIntersect = data.getGeoTools().getClosestProjectedLinearLocationOnLineString(coordinate, linkSegmentGeometry);
    var closestLinkIntersectCoordinate = closestLinkIntersect.getCoordinate(linkSegmentGeometry);

    var dirPos1 = data.getGeoTools().toDirectPosition(closestLinkIntersectCoordinate);
    var dirPos2 =  data.getGeoTools().toDirectPosition(coordinate);
    return data.getGeoTools().getAzimuthInDegrees(dirPos1,dirPos2, true);
  }
}
