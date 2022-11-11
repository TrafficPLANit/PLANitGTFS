package org.goplanit.gtfs.util;

import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.modifier.event.GraphModifierListener;
import org.goplanit.utils.misc.CollectionUtils;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.zoning.modifier.event.handler.UpdateDirectedConnectoidsOnBreakLinkSegmentHandler;
import org.locationtech.jts.geom.Point;

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
    return GeoContainerUtils.queryEdgeQuadtree(data.getGeoIndexedExistingLinks(), searchEnvelope);
  }

  /** Extract/create a PLANit node based on the given location. Either it already exists as a PLANit node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the PLANit node. When we break the link all relevant GTFS relate state
   * data is also updated to reflect the enw situation appropriately
   *
   * @param gtfsStopNodeLocation to collect/create PLANit node for
   * @param referenceLink the location is related to, can be internal can be an extreme node
   * @param networkLayer to extract node on
   * @param temporaryListeners graph modifier listeners to apply when  breaking link(s) to obtain PLANit node
   * @param data containing state
   * @return PLANit node collected/created
   */
  public static Node extractNodeByLinkGeometryLocation(
      Point gtfsStopNodeLocation, MacroscopicLink referenceLink, MacroscopicNetworkLayer networkLayer, Collection<GraphModifierListener> temporaryListeners, GtfsZoningHandlerData data){
    PlanItRunTimeException.throwIfNull(gtfsStopNodeLocation, "Stop node location is null, not allowed");
    PlanItRunTimeException.throwIfNull(referenceLink, "Designated access link for GTFS stop is null, not allowed");

    /* prefer to use extreme node of access link which avoids breaking links, but it should be the same or extremely close to the extreme node to be eligible */
    final double extremeNodeMaxDistanceMeters = 5.0;

    Node planitNode = data.getGeoTools().isDistanceWithinMetres(referenceLink.getNodeA().getPosition(), gtfsStopNodeLocation, extremeNodeMaxDistanceMeters) ?  referenceLink.getNodeA() : null;
    planitNode = data.getGeoTools().isDistanceWithinMetres(referenceLink.getNodeB().getPosition(), gtfsStopNodeLocation, extremeNodeMaxDistanceMeters) ?  referenceLink.getNodeB() : planitNode;
    if(planitNode != null) {
      return planitNode;
    }

    /* location is internal to an existing link, create it based on osm node if possible, otherwise base it solely on location provided*/
    planitNode = networkLayer.getNodes().getFactory().registerNew(gtfsStopNodeLocation, true);

    /* now perform the breaking of links at the given node and update related tracking/reference information to broken link(segment)(s) where needed */
    breakLinksAtPlanitNode(planitNode, networkLayer, Collections.singleton(referenceLink), temporaryListeners, data);

    return planitNode;
  }

  /**
   * break a PLANit link at the PLANit node location while also updating all OSM related tracking indices and/or PLANit network link and link segment reference
   * that might be affected by this process:
   * <ul>
   * <li>tracking of OSM ways with multiple PLANit links</li>
   * <li>connectoid access link segments affected by breaking of link (if any)</li>
   * </ul>
   *  @param planitNode to break link at
   *
   * @param networkLayer       the node and link(s) reside on
   * @param linksToBreak       the links to break
   * @param temporaryListeners graph modifier listeners to register and apply when  breaking link(s) at PLANit node location, and unregister afterwards
   * @param data containing state
   */
  public static void breakLinksAtPlanitNode(
      final Node planitNode, final MacroscopicNetworkLayer networkLayer, final Collection<MacroscopicLink> linksToBreak, Collection<GraphModifierListener> temporaryListeners, GtfsZoningHandlerData data){

    /* add listeners */
    if(!CollectionUtils.nullOrEmpty(temporaryListeners)) {
      temporaryListeners.forEach(l -> networkLayer.getLayerModifier().addListener(l));
    }

    /* LOCAL TRACKING DATA CONSISTENCY  - BEFORE */
    {
      /* remove links from spatial index when they are broken up and their geometry changes, after breaking more links exist with smaller geometries... insert those after as replacements*/
      data.removeGeoIndexedLinks(linksToBreak);
    }

    /* break links */
    Map<Long, Set<MacroscopicLink>> newlyBrokenLinks = null; // TODO continue here see below, but first fix the creation of the connectoid listener that is passed in
//    Map<Long, Set<MacroscopicLink>> newlyBrokenLinks = OsmNetworkHandlerHelper.breakLinksWithInternalNode(
//        planitNode, linksToBreak, networkLayer, getSettings().getReferenceNetwork().getCoordinateReferenceSystem());

    /* TRACKING DATA CONSISTENCY - AFTER */
    {
      /* insert created/updated links and their geometries to spatial index instead */
      newlyBrokenLinks.forEach( (id, links) -> data.addGeoIndexedLinks(links));
    }

    /* remove listeners */
    if(!CollectionUtils.nullOrEmpty(temporaryListeners)) {
      temporaryListeners.forEach(l -> networkLayer.getLayerModifier().removeListener(l));
    }
  }
}
