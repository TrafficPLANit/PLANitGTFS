package org.goplanit.gtfs.util;

import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.TransferConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.ZoneConnectoidType;

import java.util.Collection;
import java.util.Set;

/**
 * Utils class related to GTFS and PLANit directed connectoids functionality
 *
 * @author markr
 */
public class GtfsDirectedConnectoidHelper {

  /** no direct GTFS external id for connectoid, but signify source */
  public static final String GTFS_CONNECTOID_EXTERNAL_INFERRED_ID = "gtfs_inferred";

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
      data.addDirectedConnectoidByLocation(
              networkLayer, newConnectoid.getReferenceVertex().getPosition() ,newConnectoid);
      /* 2) index connectoids on transfer zone, so we can collect it by transfer zone as well */
      data.registerTransferZoneToConnectoidModes(transferZone, type, newConnectoid, allowedModes);

      data.getProfiler().incrementCreatedConnectoids();
    }

    return createdConnectoids;
  }
}
