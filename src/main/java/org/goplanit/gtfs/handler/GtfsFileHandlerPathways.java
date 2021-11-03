package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.model.GtfsPathway;
import org.goplanit.gtfs.scheme.GtfsPathwaysScheme;

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
