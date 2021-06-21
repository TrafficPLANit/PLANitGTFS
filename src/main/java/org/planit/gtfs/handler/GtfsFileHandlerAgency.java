package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsAgency;
import org.planit.gtfs.scheme.GtfsAgenciesScheme;

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
