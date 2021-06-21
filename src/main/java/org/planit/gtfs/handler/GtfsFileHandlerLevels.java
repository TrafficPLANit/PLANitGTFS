package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsLevel;
import org.planit.gtfs.scheme.GtfsLevelsScheme;

/**
 * Base handler for handling levels
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerLevels extends GtfsFileHandler<GtfsLevel> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerLevels() {
    super(new GtfsLevelsScheme());
  }

  /**
   * Handle a GTFS level
   */
  @Override
  public void handle(GtfsLevel gtfsLevel) {
    /* to be implemented by derived class, or ignore */
  }

}
