package org.goplanit.gtfs.util;

import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;

import java.util.Collection;
import java.util.Set;

/**
 * Utils class related to GTFS and PLANit directed connectoids functionality
 *
 * @author markr
 */
public class GtfsDirectedConnectoidHelper {

  /** create directed connectoids, one per link segment provided, all related to the given transfer zone and with access modes provided. connectoids are only created
   * when the access link segment has at least one of the allowed modes as an eligible mode
   *
   * @param transferZone to relate connectoids to
   * @param networkLayer of the modes and link segments used
   * @param linkSegments to create connectoids for (one per segment)
   * @param allowedModes used for each connectoid
   * @param data containing state
   * @return created connectoids
   */
  public static Collection<DirectedConnectoid> createAndRegisterDirectedConnectoids(
      final TransferZone transferZone, final MacroscopicNetworkLayer networkLayer, final Iterable<? extends EdgeSegment> linkSegments, final Set<Mode> allowedModes, GtfsZoningHandlerData data){

    Collection<DirectedConnectoid> createdConnectoids = ZoningConverterUtils.createAndRegisterDirectedConnectoids(data.getZoning(), transferZone, (Iterable<MacroscopicLinkSegment>) linkSegments, allowedModes);
    for(var newConnectoid : createdConnectoids) {
      /* update GTFS parsing specific PLANit data tracking information */

      /* 1) index by access link segment's downstream node location */
      data.addDirectedConnectoidByLocation(networkLayer, newConnectoid.getAccessLinkSegment().getDownstreamVertex().getPosition() ,newConnectoid);
      /* 2) index connectoids on transfer zone, so we can collect it by transfer zone as well */
      data.registerTransferZoneToConnectoidModes(transferZone, newConnectoid, allowedModes);

      data.getProfiler().incrementCreatedConnectoids();
    }

    return createdConnectoids;
  }
}
