package org.goplanit.gtfs.test;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What must hold of the intersections of a network after GTFS has been integrated into it.
 * <p>
 * Integrating GTFS breaks links at nodes to place stops. Each intersection must survive this, and must refer only to
 * what is still in its layer: its member nodes, approach segments that end at one of its member nodes, and internal
 * segments between its member nodes.
 * </p>
 *
 * @author markr
 */
public final class IntersectionAssertions {

  /**
   * Count the intersections over all layers of the network
   *
   * @param network to count for
   * @return number of intersections
   */
  public static int countIntersections(final MacroscopicNetwork network) {
    int count = 0;
    for (var layer : network.getTransportLayers()) {
      count += layer.getIntersections().size();
    }
    return count;
  }

  /**
   * Assert the network still has all intersections of the input, at least one, and that each refers only to entities
   * of its layer
   *
   * @param inputIntersectionCount number of intersections of the network before GTFS was integrated
   * @param network after GTFS was integrated
   */
  public static void assertIntersectionsKeptAndConsistent(
      final int inputIntersectionCount, final MacroscopicNetwork network) {
    assertTrue(inputIntersectionCount > 0);
    assertEquals(inputIntersectionCount, countIntersections(network));

    for (var layer : network.getTransportLayers()) {
      for (var intersection : layer.getIntersections()) {
        for (var node : intersection.getMemberNodes()) {
          assertSame(node, layer.getNodes().get(node.getId()));
        }
        for (var approach : intersection.getApproachSegments()) {
          assertSame(approach, layer.getLinkSegments().get(approach.getId()));
          assertTrue(intersection.hasMemberNode(approach.getDownstreamVertex()));
        }
        for (var internal : IntersectionUtils.getInternalSegmentsOf(intersection)) {
          assertSame(internal, layer.getLinkSegments().get(internal.getId()));
          assertTrue(intersection.hasMemberNode(internal.getUpstreamVertex()));
          assertTrue(intersection.hasMemberNode(internal.getDownstreamVertex()));
        }
      }
    }
  }
}
