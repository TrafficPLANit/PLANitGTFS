package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsAttribution;
import org.planit.gtfs.scheme.GtfsAttributionsScheme;

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
