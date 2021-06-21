package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsStopTime;
import org.planit.gtfs.scheme.GtfsStopTimesScheme;

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
