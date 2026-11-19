package org.goplanit.gtfs.util;

import org.goplanit.converter.zoning.ZoningConverterModifierUtils;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityOrigin;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityType;
import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Node;
import org.locationtech.jts.geom.Point;

import java.util.Collection;

/**
 * Utils class related to GTFS and PLANit link functionality
 *
 * @author markr
 */
public class GtfsLinkHelper {

  /**
   * Record what breaking a link open to reach a stop adds to the physical network, being the node the stop is
   * reached from, the additional link the break leaves behind, and a segment per direction the link carries
   *
   * @param linkToBreak the link broken, kept as the piece up to the new node, with the segments it had
   * @param data containing what the outcome is recorded into
   */
  private static void registerBrokenOpenForStopAccess(
      final MacroscopicLink linkToBreak, final GtfsZoningHandlerData data){
    var planitEntityDiagnostics = data.getProfiler().getPlanitEntityDiagnostics();
    var origin = GtfsPlanitEntityOrigin.BROKEN_OPEN_FOR_STOP_ACCESS.getSubType();
    planitEntityDiagnostics.registerDesired(GtfsPlanitEntityType.NODE, origin, 1);
    planitEntityDiagnostics.registerDesired(GtfsPlanitEntityType.LINK, origin, 1);
    planitEntityDiagnostics.registerDesired(
        GtfsPlanitEntityType.LINK_SEGMENT, origin,
        (linkToBreak.hasLinkSegmentAb() ? 1 : 0) + (linkToBreak.hasLinkSegmentBa() ? 1 : 0));
  }

  /**
   * Verify if an end node of an access link may be reused as the access node of a stop, rather than breaking the link.
   * It may, unless it is a dead end for the access mode: no link there other than the access link has a segment the
   * mode may use. A dead end is only reached in the direction running into it, so the link is broken at the stop
   * instead, letting both directions reach it. Ferries are the exception: a vessel turns around at a wharf, so a wharf
   * at a dead end serves it fully.
   *
   * @param endNode of the access link
   * @param accessLink the stop is reached along
   * @param accessMode of the stop
   * @return true when it may be reused, false otherwise
   */
  public static boolean isEndNodeReusableAsAccessNode(
      final Node endNode, final MacroscopicLink accessLink, final Mode accessMode){
    if(accessMode.isPredefinedModeType(PredefinedModeType.FERRY)){
      return true;
    }
    return endNode.<MacroscopicLink>getLinks().stream().anyMatch(
        link -> !link.idEquals(accessLink) && link.isModeAllowedOnAnySegment(accessMode));
  }

   /**
   * Find nearby links based on a given search radius
   * @param locationInPlanitCrs point location to search around
   * @param pointSearchRadiusMeters search radius to apply
   * @param data containing state
   * @return found links around this location (in network CRS)
   */
  public static Collection<MacroscopicLink> findNearbyLinks(
      Point locationInPlanitCrs, double pointSearchRadiusMeters, GtfsZoningHandlerData data) {

    var searchEnvelope = data.getGeoToolsInPlanitCrs().createBoundingBox(
        locationInPlanitCrs.getX(), locationInPlanitCrs.getY() ,pointSearchRadiusMeters);
    return data.getConverterData().findLinksSpatiallyAcrossLayers(searchEnvelope);
  }

  /** Extract/create a PLANit node based on the given location. Either it already exists as a PLANit node, or it
   * is internal to an existing link. In the latter case a new node is created and the existing link is broken. In
   * the former case, we simply collect the PLANit node, which happens only when the location is an end node of the
   * link itself: whether a nearby end node is reused instead is decided where the location is found. See
   * {@link ZoningConverterModifierUtils#extractNodeByLinkGeometryLocation}. When we break the link, what breaking it adds
   * is recorded for the stop access statistics
   *
   * @param stopLocationInPlanitCrs to collect/create PLANit node for (can be slightly altered in case of
   *                                rounding/syncing
   *                             with reference link geometry)
   * @param referenceLink the location is related to, can be internal can be an extreme node
   * @param networkLayer to extract node on
   * @param data containing state
   * @return PLANit node collected/created, and flag indicating if this required breaking a link and node is
   * newly created (true), false otherwise
   */
  public static Pair<Node,Boolean> extractNodeByLinkGeometryLocation(
      Point stopLocationInPlanitCrs,
      final MacroscopicLink referenceLink,
      final MacroscopicNetworkLayer networkLayer,
      final GtfsZoningHandlerData data){

    var nodeAndBroken = ZoningConverterModifierUtils.extractNodeByLinkGeometryLocation(
        data.getConverterData(), stopLocationInPlanitCrs, referenceLink, networkLayer);
    /* the link broken is kept as the piece up to the new node, with the segments it had, so it tells what the break
     * added, recorded here where the break happens rather than inferred afterwards from what the network grew by */
    if(nodeAndBroken.second()){
      registerBrokenOpenForStopAccess(referenceLink, data);
    }
    return nodeAndBroken;
  }
}
