package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.model.GtfsAgency;
import org.goplanit.gtfs.scheme.GtfsAgenciesScheme;

/**
 * Base handler for handling agencies
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerAgency extends GtfsFileHandler<GtfsAgency> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerAgency() {
    super(new GtfsAgenciesScheme());
  }

  /**
   * Handle a GTFS agency
   */
  @Override
  public void handle(GtfsAgency gtfsAgency) {
    /* to be implemented by derived class, or ignore */
  }

}
