package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.scheme.GtfsStopsScheme;

/**
 * Base handler for handling stops
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerStops extends GtfsFileHandler<GtfsStop> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerStops() {
    super(new GtfsStopsScheme());
  }

  /**
   * Handle a GTFS stop
   */
  @Override
  public void handle(GtfsStop gtfsStop) {
    /* to be implemented by derived class, or ignore */
  }

}
