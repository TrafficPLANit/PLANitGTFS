package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsAttribution;
import org.goplanit.gtfs.scheme.GtfsAttributionsScheme;

/**
 * Base handler for handling attributions
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerAttributions extends GtfsFileHandler<GtfsAttribution> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerAttributions() {
    super(new GtfsAttributionsScheme());
  }

  /**
   * Handle a GTFS attributions
   */
  @Override
  public void handle(GtfsAttribution gtfsAttribution) {
    /* to be implemented by derived class, or ignore */
  }

}
