package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.zoning.GtfsPublicTransportReaderSettings;
import org.goplanit.gtfs.entity.GtfsRoute;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandlerRoutes;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneType;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.referencing.operation.MathTransform;

import java.util.*;
import java.util.logging.Logger;

/**
 * Handler for handling routes and populating a PLANit (Service) network and routes with the found GTFS routes
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerRoutes extends GtfsFileHandlerRoutes {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerRoutes.class.getCanonicalName());

  /** profiler to use */
  private final GtfsZoningHandlerProfiler profiler;

  /** service network to populate */
  private final ServiceNetwork serviceNetwork;

  /** settings containing configuration */
  private final GtfsPublicTransportReaderSettings settings;

  /**
   * Initialise this handler
   */
  private void initialise(){
    //todo
  }

  /**
   * Constructor
   *
   * @param serviceNetworkToPopulate the PLANit zoning instance to populate (further)
   * @param settings to apply where needed
   * @param profiler to use
   */
  public GtfsPlanitFileHandlerRoutes(final ServiceNetwork serviceNetworkToPopulate, final GtfsPublicTransportReaderSettings settings, final GtfsZoningHandlerProfiler profiler) {
    super();
    this.profiler = profiler;
    this.serviceNetwork = serviceNetworkToPopulate;
    this.settings = settings;
    initialise();
  }

  /**
   * Handle a GTFS route
   */
  @Override
  public void handle(GtfsRoute gtfsRoute) {
    //todo
  }

}
