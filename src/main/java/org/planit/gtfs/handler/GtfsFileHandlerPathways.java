package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsPathway;
import org.planit.gtfs.scheme.GtfsPathwaysScheme;

/**
 * Base handler for handling pathways
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerPathways extends GtfsFileHandler<GtfsPathway> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerPathways() {
    super(new GtfsPathwaysScheme());
  }

  /**
   * Handle a GTFS pathway
   */
  @Override
  public void handle(GtfsPathway gtfsPathway) {
    /* to be implemented by derived class, or ignore */
  }

}
