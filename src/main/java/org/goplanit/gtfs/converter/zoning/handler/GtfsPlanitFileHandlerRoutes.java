package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.entity.GtfsRoute;
import org.goplanit.gtfs.handler.GtfsFileHandlerRoutes;
import org.goplanit.network.ServiceNetwork;

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
  private final GtfsZoningReaderSettings settings;

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
  public GtfsPlanitFileHandlerRoutes(final ServiceNetwork serviceNetworkToPopulate, final GtfsZoningReaderSettings settings, final GtfsZoningHandlerProfiler profiler) {
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
