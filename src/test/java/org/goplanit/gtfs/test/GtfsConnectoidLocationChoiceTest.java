package org.goplanit.gtfs.test;

import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.gtfs.util.GtfsDirectedConnectoidHelper;
import org.goplanit.gtfs.util.GtfsLinkHelper;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.layer.macroscopic.AccessGroupPropertiesFactory;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.goplanit.utils.network.layer.physical.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests choosing the connectoid location of a new GTFS stop among its candidate access segments, on small hand-made
 * light rail networks in metres
 *
 * @author markr
 */
public class GtfsConnectoidLocationChoiceTest {

  /** search radius from a stop to its access link, the GTFS zoning default */
  private static final double STOP_TO_LINK_SEARCH_RADIUS_M = 40;

  private MacroscopicNetworkLayer layer;

  private Mode lightRail;

  private Mode ferry;

  private MacroscopicLinkSegmentType track;

  private MacroscopicLinkSegmentType ferryRoute;

  private PlanitJtsCrsUtils geoUtils;

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
    var network = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken());
    network.setCoordinateReferenceSystem(PlanitJtsCrsUtils.CARTESIANCRS);
    lightRail = network.getModes().getFactory().registerNew(PredefinedModeType.LIGHTRAIL);
    ferry = network.getModes().getFactory().registerNew(PredefinedModeType.FERRY);
    layer = network.getTransportLayers().getFactory().registerNew();
    layer.registerSupportedModes(List.of(lightRail, ferry));
    track = layer.getLinkSegmentTypes().getFactory().registerNew("track", 1800, 180);
    AccessGroupPropertiesFactory.createOnLinkSegmentType(track, 20, List.of(lightRail));
    ferryRoute = layer.getLinkSegmentTypes().getFactory().registerNew("ferryRoute", 1800, 180);
    AccessGroupPropertiesFactory.createOnLinkSegmentType(ferryRoute, 20, List.of(ferry));
    geoUtils = new PlanitJtsCrsUtils(PlanitJtsCrsUtils.CARTESIANCRS);
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** a node at the given position */
  private Node node(double x, double y) {
    var node = layer.getNodes().getFactory().registerNew();
    node.setPosition(PlanitJtsUtils.createPoint(x, y));
    return node;
  }

  /** a two-way track link from node a to node b, passing through the given shape points in between */
  private MacroscopicLink twoWayTrack(Node a, Node b, Coordinate... shapePoints) {
    return twoWayLink(track, a, b, shapePoints);
  }

  /** a two-way link of the given type from node a to node b, passing through the given shape points in between */
  private MacroscopicLink twoWayLink(MacroscopicLinkSegmentType type, Node a, Node b, Coordinate... shapePoints) {
    var coordinates = new Coordinate[shapePoints.length + 2];
    coordinates[0] = a.getPosition().getCoordinate();
    System.arraycopy(shapePoints, 0, coordinates, 1, shapePoints.length);
    coordinates[coordinates.length - 1] = b.getPosition().getCoordinate();
    var geometry = PlanitJtsUtils.createLineString(coordinates);
    var link = layer.getLinks().getFactory().registerNew(a, b, geometry.getLength() / 1000, true);
    link.setGeometry(geometry);
    layer.getLinkSegments().getFactory().registerNew(link, type, true, true);
    layer.getLinkSegments().getFactory().registerNew(link, type, false, true);
    return link;
  }

  /** choose the connectoid location for a light rail stop, finding the location on each candidate as the GTFS
   * zoning reader does */
  private Point chooseConnectoidLocation(Point stop, Collection<MacroscopicLinkSegment> candidates) {
    return chooseConnectoidLocation(stop, candidates, lightRail);
  }

  /** choose the connectoid location for a stop of the given mode, finding the location on each candidate as the GTFS
   * zoning reader does */
  private Point chooseConnectoidLocation(Point stop, Collection<MacroscopicLinkSegment> candidates, Mode mode) {
    return GtfsDirectedConnectoidHelper.chooseConnectoidLocation(
        stop,
        candidates,
        candidate -> ZoningConverterUtils.findConnectoidLocationForWaitingAreaOnLinkSegment(
            "stop", stop, candidate, candidate.getParent().getExternalId(), mode, STOP_TO_LINK_SEARCH_RADIUS_M,
            null, null, null, CountryNames.AUSTRALIA, geoUtils),
        mode,
        geoUtils);
  }

  /**
   * A stop near the dead end of a two-way track is not given the dead end, which only the direction running into it
   * reaches. The link is broken at the stop's projection onto it instead, also within reach of the dead end, so both
   * directions reach the stop
   */
  @Test
  public void deadEndNotReusedButLinkBrokenAtStopProjection() {
    /*
     *   A ======================================= P ========== X == B     two-way light rail A-P-B, P a shape point,
     *  (0,0)                                    (75,0)     (98,0) (100,0)  B a dead end
     *                                                        s
     *                                                      (98,4) stop
     *
     *   along A->B the location is dead end B, 4.5 m from the stop: replaced by the stop's projection X, 4 m from it
     *   along B->A it is shape point P, 23.3 m from the stop
     */
    var a = node(0, 0);
    var b = node(100, 0);
    var link = twoWayTrack(a, b, new Coordinate(75, 0));

    var chosen = chooseConnectoidLocation(
        PlanitJtsUtils.createPoint(98, 4), List.of(link.getLinkSegmentAb(), link.getLinkSegmentBa()));

    assertNotNull(chosen);
    assertEquals(98, chosen.getX(), 1e-6);
    assertEquals(0, chosen.getY(), 1e-6);
    assertFalse(GtfsLinkHelper.isEndNodeReusableAsAccessNode(b, link, lightRail));
  }

  /**
   * Of the candidate locations, the one nearest to the stop is chosen, here the nearer of two end nodes that both
   * directions reach
   */
  @Test
  public void nearestLocationWins() {
    /*
     *   C ========== A ====================== B ========== D     two-way light rail C-A, A-B and B-D
     * (-40,0)      (0,0)                   (40,0)       (80,0)
     *                                    s
     *                                  (30,2) stop
     *
     *   along A->B the location is B, 10.2 m from the stop; along B->A it is A, 30.1 m from it
     */
    var c = node(-40, 0);
    var a = node(0, 0);
    var b = node(40, 0);
    var d = node(80, 0);
    twoWayTrack(c, a);
    var link = twoWayTrack(a, b);
    twoWayTrack(b, d);

    var chosen = chooseConnectoidLocation(
        PlanitJtsUtils.createPoint(30, 2), List.of(link.getLinkSegmentBa(), link.getLinkSegmentAb()));

    assertNotNull(chosen);
    assertEquals(40, chosen.getX(), 1e-6);
    assertEquals(0, chosen.getY(), 1e-6);
  }

  /**
   * A ferry stop near the wharf at the dead end of its route is given the wharf node, so the link is not broken: a
   * vessel turns around at the wharf, so the wharf serves it fully
   */
  @Test
  public void ferryStopReusesDeadEndWharfNode() {
    /*
     *   A ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ P ~~~~~~~~~ W     two-way ferry route A-P-W, P a shape point,
     *  (0,0)                               (75,0)      (100,0)  W the wharf, a dead end
     *                                                    s
     *                                                  (98,4) stop
     *
     *   along A->W the location is wharf W, 4.5 m from the stop; along W->A it is shape point P, 23.3 m from it
     */
    var a = node(0, 0);
    var wharf = node(100, 0);
    var route = twoWayLink(ferryRoute, a, wharf, new Coordinate(75, 0));

    var chosen = chooseConnectoidLocation(
        PlanitJtsUtils.createPoint(98, 4), List.of(route.getLinkSegmentAb(), route.getLinkSegmentBa()), ferry);

    assertNotNull(chosen);
    assertEquals(100, chosen.getX(), 1e-6);
    assertEquals(0, chosen.getY(), 1e-6);
    assertTrue(GtfsLinkHelper.isEndNodeReusableAsAccessNode(wharf, route, ferry));
  }

  /**
   * An end node near the stop that both directions reach is reused, without breaking the link, also when a farther
   * location is reached from more directions
   */
  @Test
  public void endNodeBothDirectionsReachIsReusedOverFartherLocationReachedFromMore() {
    /*
     *                                         E (40,40)
     *                                         |
     *   C ========== A ====================== B ========== D     two-way light rail C-A, A-B, B-D and B-E
     * (-40,0)      (0,0)                   (40,0)       (80,0)
     *                 s
     *               (3,2) stop
     *
     *   along B->A the location is A, 3.6 m from the stop, reached from C and B
     *   along A->B it is B, 37.1 m from the stop, reached from A, D and E
     */
    var c = node(-40, 0);
    var a = node(0, 0);
    var b = node(40, 0);
    var d = node(80, 0);
    var e = node(40, 40);
    twoWayTrack(c, a);
    var link = twoWayTrack(a, b);
    twoWayTrack(b, d);
    twoWayTrack(b, e);

    var chosen = chooseConnectoidLocation(
        PlanitJtsUtils.createPoint(3, 2), List.of(link.getLinkSegmentAb(), link.getLinkSegmentBa()));

    assertNotNull(chosen);
    assertEquals(0, chosen.getX(), 1e-6);
    assertEquals(0, chosen.getY(), 1e-6);
    assertTrue(GtfsLinkHelper.isEndNodeReusableAsAccessNode(a, link, lightRail));
  }

  /**
   * A stop past the dead end of a two-way track cannot have the link broken at its projection, which is the dead end
   * itself. Another candidate's location along the track is chosen instead, although farther, so both directions reach
   * the stop
   */
  @Test
  public void stopPastDeadEndGetsLocationAlongTrackInstead() {
    /*
     *   A ======================================= P ============= B          two-way light rail A-P-B, P a shape point,
     *  (0,0)                                    (75,0)        (100,0)        B a dead end
     *                                                                    s
     *                                                                (110,3) stop, past the dead end
     *
     *   along A->B the location is dead end B, 10.4 m from the stop, and the stop's projection is B itself
     *   along B->A it is shape point P, 35.1 m from the stop
     */
    var a = node(0, 0);
    var b = node(100, 0);
    var link = twoWayTrack(a, b, new Coordinate(75, 0));

    var chosen = chooseConnectoidLocation(
        PlanitJtsUtils.createPoint(110, 3), List.of(link.getLinkSegmentAb(), link.getLinkSegmentBa()));

    assertNotNull(chosen);
    assertEquals(75, chosen.getX(), 1e-6);
    assertEquals(0, chosen.getY(), 1e-6);
  }

  /**
   * A stop past the dead end of a two-way track is still given the dead end when no other candidate has a location
   */
  @Test
  public void stopPastDeadEndWithNoOtherCandidateGetsDeadEnd() {
    /*
     *   A ======================================= P ============= B          two-way light rail A-P-B, P a shape point,
     *  (0,0)                                    (75,0)        (100,0)        B a dead end
     *                                                                    s
     *                                                                (110,3) stop, past the dead end
     *
     *   only A->B is a candidate: its location is dead end B, the stop's projection being B itself
     */
    var a = node(0, 0);
    var b = node(100, 0);
    var link = twoWayTrack(a, b, new Coordinate(75, 0));

    var chosen = chooseConnectoidLocation(PlanitJtsUtils.createPoint(110, 3), List.of(link.getLinkSegmentAb()));

    assertNotNull(chosen);
    assertEquals(100, chosen.getX(), 1e-6);
    assertEquals(0, chosen.getY(), 1e-6);
  }
}
