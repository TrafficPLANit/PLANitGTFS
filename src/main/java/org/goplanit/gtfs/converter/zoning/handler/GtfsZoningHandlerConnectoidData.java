package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.converter.zoning.ZoningConverterConnectoidData;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.zoning.TransferConnectoid;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Point;

import java.util.*;
import java.util.logging.Logger;

/**
 * Zoning handler data specifically tailored towards connectoids. For our GTFS version we initialise with any existing
 * connectoids from the service network.
 *
 * @author markr
 */
public class GtfsZoningHandlerConnectoidData extends ZoningConverterConnectoidData {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningHandlerConnectoidData.class.getCanonicalName());

  /**
   * Constructor
   * @param serviceNetwork to use
   * @param referenceZoning to use
   */
  public GtfsZoningHandlerConnectoidData(ServiceNetwork serviceNetwork, Zoning referenceZoning){
    //TODO: no support yet for OD connectoids, meaning that if links are broken the connectoid is potentially moved
    // for OD zones
    super(referenceZoning, serviceNetwork.getParentNetwork(),
        referenceZoning.getTransferConnectoids().groupByPhysicalLayerAndCustomKey(
            serviceNetwork.getParentNetwork().getTransportLayers(),
            d -> d.getReferenceVertex().getPosition()));
  }

}
