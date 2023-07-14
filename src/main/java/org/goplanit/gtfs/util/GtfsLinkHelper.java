package org.goplanit.gtfs.util;

import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.modifier.event.GraphModifierListener;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.misc.CollectionUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.ConnectoidUtils;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.zoning.modifier.event.handler.UpdateDirectedConnectoidsOnBreakLinkSegmentHandler;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LinearLocation;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jts.util.CoordinateArrayFilter;

import java.util.*;

/**
 * Utils class related to GTFS and PLANit link functionality
 *
 * @author markr
 */
public class GtfsLinkHelper {

  /**
   * Find nearby links based on a given search radius
   * @param location point location to search around (in WGS84 CRS)
   * @param pointSearchRadiusMeters search radius to apply
   * @param data containing state
   * @return found links around this location (in network CRS)
   */
  public static Collection<MacroscopicLink> findNearbyLinks(Point location, double pointSearchRadiusMeters, GtfsZoningHandlerData data) {
    //todo change implementation so it does not necessarily require WGS84 input locations as it is inconsistent with the utils class
    var searchEnvelope = data.getGeoTools().createBoundingBox(location.getX(),location.getY(),pointSearchRadiusMeters);
    searchEnvelope = PlanitJtsUtils.transformEnvelope(searchEnvelope, data.getCrsTransform());
    return GeoContainerUtils.queryEdgeQuadtree(data.getGeoIndexedLinks(), searchEnvelope);
  }

  /** Extract/create a PLANit node based on the given location. Either it already exists as a PLANit node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the PLANit node. When we break the link all relevant GTFS relate state
   * data is also updated to reflect the enw situation appropriately
   *
   * @param gtfsStopNodeLocation to collect/create PLANit node for (can be slightly altered in case of rounding/syncing with reference link geometry)
   * @param referenceLink the location is related to, can be internal can be an extreme node
   * @param networkLayer to extract node on
   * @param data containing state
   * @return PLANit node collected/created, and flag indicating if this required breaking a link and node is newly created (true), false otherwise
   */
  public static Pair<Node,Boolean> extractNodeByLinkGeometryLocation(
      Point gtfsStopNodeLocation, final MacroscopicLink referenceLink, final MacroscopicNetworkLayer networkLayer, final GtfsZoningHandlerData data){
    PlanItRunTimeException.throwIfNull(gtfsStopNodeLocation, "Stop node location is null, not allowed");
    PlanItRunTimeException.throwIfNull(referenceLink, "Designated access link for GTFS stop is null, not allowed");

    /* prefer to use extreme node of access link which avoids breaking links, but it should be the same or extremely close to the extreme node to be eligible */
    final double extremeNodeMaxDistanceMeters = 5.0;
    Node planitNode = data.getGeoTools().isDistanceWithinMetres(referenceLink.getNodeA().getPosition(), gtfsStopNodeLocation, extremeNodeMaxDistanceMeters) ?  referenceLink.getNodeA() : null;
    planitNode = data.getGeoTools().isDistanceWithinMetres(referenceLink.getNodeB().getPosition(), gtfsStopNodeLocation, extremeNodeMaxDistanceMeters) ?  referenceLink.getNodeB() : planitNode;
    if(planitNode != null) {
      return Pair.of(planitNode, Boolean.FALSE);
    }

    // New node required

    /* in case location is not yet an explicit point on the link's geometry, we explicitly inject it to allow for link breaking at this point (search with minimal margin to avoid missing a match due to
       precision issues in case the link was persisted to disk and parsed with reduced precision */
    int existingCoordinatePosition = PlanitJtsUtils.findFirstCoordinatePosition(gtfsStopNodeLocation.getCoordinate(), referenceLink.getGeometry(), Precision.EPSILON_6).orElse(-1);
    if(existingCoordinatePosition<0){
      LinearLocation projectedLinearLocationOnLink = PlanitEntityGeoUtils.extractClosestProjectedLinearLocationToGeometryFromEdge(gtfsStopNodeLocation, referenceLink, data.getGeoTools());
      referenceLink.updateGeometryInjectCoordinateAtProjectedLocation(projectedLinearLocationOnLink);
      /* update coordinate position */
      existingCoordinatePosition = PlanitJtsUtils.findFirstCoordinatePosition(gtfsStopNodeLocation.getCoordinate(), referenceLink.getGeometry(), Precision.EPSILON_6).orElse(-1);
    }
    /* to make sure the location of the node and that of the link is exactly the same (avoid rounding due to tolerance used abve in matching we update the location used to the link's internal location that was created/existed. */
    gtfsStopNodeLocation = PlanitJtsUtils.createPoint(referenceLink.getGeometry().getCoordinateN(existingCoordinatePosition));

    /* location is internal to an existing link, create new PLANit node at this location to serve as the new point of demarcation for the link that is to be broken */
    planitNode = networkLayer.getNodes().getFactory().registerNew(gtfsStopNodeLocation, true);

    /* register additional actions on breaking link via listener for connectoid update (see above) as connectoids and their access links might be affected/invalidated when
    * breaking links, this listener accounts for that */
    /* TODO: refactor this so it does not require this whole preparing of data. Ideally this is handled more elegantly than now */
    Map<Point, DirectedConnectoid> connectoidsAccessNodeLocationBeforeBreakLink =
        ConnectoidUtils.findDirectedConnectoidsReferencingLinks(List.of(referenceLink), data.getDirectedConnectoidsByLocation(networkLayer));
    GraphModifierListener listener = new UpdateDirectedConnectoidsOnBreakLinkSegmentHandler(connectoidsAccessNodeLocationBeforeBreakLink);

    /* now perform the breaking of links at the given node and update related tracking/reference information to broken link(segment)(s) where needed */
    breakLinksAtPlanitNode(planitNode, networkLayer, referenceLink, List.of(listener), data);

    return Pair.of(planitNode, Boolean.TRUE);
  }

  /**
   * break a PLANit link at the PLANit node location while also updating all related tracking indices and/or PLANit network link and link segment references
   * that might be affected by this process:
   * <ul>
   * <li>tracking of geo indexed PLANit links</li>
   * <li>connectoid access link segments affected by breaking of link (if any)</li>
   * </ul>
   *  @param planitNode to break link at (its location, it is assumed node has already been created)
   * @param networkLayer       the node and link(s) reside on
   * @param linkToBreak       the link to break
   * @param temporaryListeners graph modifier listeners to register and apply when  breaking link(s) at PLANit node location, and unregister afterwards
   * @param data containing state
   */
  public static void breakLinksAtPlanitNode(
      final Node planitNode, final MacroscopicNetworkLayer networkLayer, final MacroscopicLink linkToBreak, Collection<GraphModifierListener> temporaryListeners, GtfsZoningHandlerData data){

    /* BEFORE - add listeners */
    if(!CollectionUtils.nullOrEmpty(temporaryListeners)) {
      temporaryListeners.forEach(l -> networkLayer.getLayerModifier().addListener(l));
    }

    {
      /* BEFORE - LOCAL TRACKING DATA CONSISTENCY */
      {
        /* remove links from spatial index when they are broken up and their geometry changes, after breaking more links exist with smaller geometries... insert those after as replacements*/
        data.removeGeoIndexedLink(linkToBreak);
      }

      /* break links */
      Map<Long, Pair<MacroscopicLink, MacroscopicLink>> newlyBrokenLinks = networkLayer.getLayerModifier().breakAt(
          linkToBreak, planitNode, data.getGeoTools().getCoordinateReferenceSystem());


      /* AFTER - TRACKING DATA CONSISTENCY */
      {
        /* insert created/updated links and their geometries to spatial index instead */
        newlyBrokenLinks.forEach( (id, linkPair) -> data.addGeoIndexedLinks(linkPair.first(), linkPair.second()));
      }
    }

    /* AFTER - remove listeners */
    if(!CollectionUtils.nullOrEmpty(temporaryListeners)) {
      temporaryListeners.forEach(l -> networkLayer.getLayerModifier().removeListener(l));
    }
  }
}
