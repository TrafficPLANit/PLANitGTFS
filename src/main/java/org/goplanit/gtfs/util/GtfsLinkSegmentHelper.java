package org.goplanit.gtfs.util;

import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.locationtech.jts.geom.Coordinate;

/**
 * Utils class related to GTFS and PLANit link segment functionality
 *
 * @author markr
 */
public class GtfsLinkSegmentHelper {

  /** Based on coordinate draw a virtual line to closest intersection point on link segment and identify the
   * azimuth (0-360 degrees) that goes with this virtual line
   *  from the intersection point on the link segment towards the coordinate provided
   *
   * @param linkSegment to use
   * @param coordinateInPlanitCrs to use
   * @param data containing state
   * @return azimuth in degrees found
   */
  public static double getAzimuthFromLinkSegmentToCoordinate(
      LinkSegment linkSegment, Coordinate coordinateInPlanitCrs, GtfsZoningHandlerData data) {
    PlanItRunTimeException.throwIfNull(linkSegment, "LinkSegment is null");
    PlanItRunTimeException.throwIfNull(coordinateInPlanitCrs, "Coordinate is null");

    var linkSegmentGeometry = linkSegment.getParent().getGeometry();
    var closestLinkIntersect = data.getGeoToolsInPlanitCrs().getClosestProjectedLinearLocationOnLineString(
        coordinateInPlanitCrs, linkSegmentGeometry);
    var closestLinkIntersectCoordinate = closestLinkIntersect.getCoordinate(linkSegmentGeometry);

    var dirPos1 = data.getGeoToolsInPlanitCrs().toDirectPosition(closestLinkIntersectCoordinate);
    var dirPos2 =  data.getGeoToolsInPlanitCrs().toDirectPosition(coordinateInPlanitCrs);
    return data.getGeoToolsInPlanitCrs().getAzimuthInDegrees(dirPos1,dirPos2, true);
  }
}
