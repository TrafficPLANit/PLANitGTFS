package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Point;

import java.util.*;
import java.util.logging.Logger;

/**
 * Zoning handler data specifically tailored towards connectoids
 *
 * @author markr
 */
public class GtfsZoningHandlerConnectoidData {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningHandlerConnectoidData.class.getCanonicalName());

  /** track created connectoids by their location and layer they reside on, needed to avoid creating duplicates when dealing with multiple modes/layers */
  private final Map<MacroscopicNetworkLayer,Map<Point, List<DirectedConnectoid>>> directedConnectoidsByLocation;

  /**
   * Constructor
   * @param referenceNetwork to use
   * @param referenceZoning to use
   */
  public GtfsZoningHandlerConnectoidData(MacroscopicNetwork referenceNetwork, Zoning referenceZoning){
    //TODO: no support yet for OD connectoids, meaning that if links are broken the connectoid is potentially moved for OD zones

    /* locate by position (point) so we can use it even if the entities/ids change */
    directedConnectoidsByLocation = referenceZoning.getTransferConnectoids().groupByPhysicalLayerAndCustomKey(
        referenceNetwork.getTransportLayers(), d -> d.getAccessNode().getPosition());
  }

  /**
   * Reset the PLANit data tracking containers
   */
  public void reset() {
    directedConnectoidsByLocation.clear();
  }

  /** collect the registered connectoids indexed by their locations for a given network layer (unmodifiable)
   *
   * @param networkLayer to use
   * @return registered directed connectoids indexed by location
   */
  public Map<Point, List<DirectedConnectoid>> getDirectedConnectoidsByLocation(MacroscopicNetworkLayer networkLayer) {
    directedConnectoidsByLocation.putIfAbsent(networkLayer, new HashMap<>());
    return Collections.unmodifiableMap(directedConnectoidsByLocation.get(networkLayer));
  }

  /** Collect the registered connectoids by given locations and network layer (unmodifiable)
   *
   * @param nodeLocation to verify
   * @param networkLayer to extract from
   * @return found connectoids (if any), otherwise null or empty set
   */
  public List<DirectedConnectoid> getDirectedConnectoidsByLocation(Point nodeLocation, MacroscopicNetworkLayer networkLayer) {
    return getDirectedConnectoidsByLocation(networkLayer).get(nodeLocation);
  }

  /** Add a connectoid to the registered connectoids indexed by their OSM id
   *
   * @param networkLayer to register for
   * @param connectoidLocation this connectoid relates to
   * @param connectoid to add
   * @return true when successful, false otherwise
   */
  public boolean addDirectedConnectoidByLocation(MacroscopicNetworkLayer networkLayer, Point connectoidLocation , DirectedConnectoid connectoid) {
    directedConnectoidsByLocation.putIfAbsent(networkLayer, new HashMap<>());
    Map<Point, List<DirectedConnectoid>> connectoidsForLayer = directedConnectoidsByLocation.get(networkLayer);
    connectoidsForLayer.putIfAbsent(connectoidLocation, new ArrayList<>(1));
    List<DirectedConnectoid> connectoids = connectoidsForLayer.get(connectoidLocation);
    if(!connectoids.contains(connectoid)) {
      return connectoids.add(connectoid);
    }
    return false;
  }

  /** Check if any connectoids have been registered for the given location on any layer
   *
   * @param location to verify
   * @return true when present, false otherwise
   */
  public boolean hasAnyDirectedConnectoidsForLocation(Point location) {
    for( var entry : directedConnectoidsByLocation.entrySet()) {
      if(hasDirectedConnectoidForLocation(entry.getKey(), location)) {
        return true;
      }
    }
    return false;
  }

  /** Check if any connectoid has been registered for the given location for this layer
   *
   * @param networkLayer to check for
   * @param point to use
   * @return true when present, false otherwise
   */
  public boolean hasDirectedConnectoidForLocation(NetworkLayer networkLayer, Point point) {
    Map<Point, List<DirectedConnectoid>>  connectoidsForLayer = directedConnectoidsByLocation.get(networkLayer);
    return connectoidsForLayer != null && connectoidsForLayer.get(point) != null && !connectoidsForLayer.get(point).isEmpty();
  }
}
