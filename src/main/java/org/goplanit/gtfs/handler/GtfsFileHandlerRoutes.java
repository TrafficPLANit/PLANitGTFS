package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsRoute;
import org.goplanit.gtfs.scheme.GtfsRoutesScheme;

/**
 * Base handler for handling routes
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerRoutes extends GtfsFileHandler<GtfsRoute> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerRoutes() {
    super(new GtfsRoutesScheme());
  }

  /**
   * Handle a GTFS route
   */
  @Override
  public void handle(GtfsRoute gtfsRoute) {
    /* to be implemented by derived class, or ignore */
  }

}
