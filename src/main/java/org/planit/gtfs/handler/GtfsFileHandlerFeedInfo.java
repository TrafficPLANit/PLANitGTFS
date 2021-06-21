package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsFeedInfo;
import org.planit.gtfs.scheme.GtfsFeedInfoScheme;

/**
 * Base handler for handling feed information
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerFeedInfo extends GtfsFileHandler<GtfsFeedInfo> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerFeedInfo() {
    super(new GtfsFeedInfoScheme());
  }

  /**
   * Handle a GTFS feed info
   */
  @Override
  public void handle(GtfsFeedInfo gtfsFeedInfo) {
    /* to be implemented by derived class, or ignore */
  }

}
