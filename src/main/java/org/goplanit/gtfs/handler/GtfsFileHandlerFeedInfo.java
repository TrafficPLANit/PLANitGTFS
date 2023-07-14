package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsFeedInfo;
import org.goplanit.gtfs.scheme.GtfsFeedInfoScheme;

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
