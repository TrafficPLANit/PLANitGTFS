package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.model.GtfsStopTime;
import org.goplanit.gtfs.scheme.GtfsStopTimesScheme;

/**
 * Base handler for handling stop times
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerStopTimes extends GtfsFileHandler<GtfsStopTime> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerStopTimes() {
    super(new GtfsStopTimesScheme());
  }

  /**
   * Handle a GTFS stop time
   */
  @Override
  public void handle(GtfsStopTime gtfsStop) {
    /* to be implemented by derived class, or ignore */
  }

}
