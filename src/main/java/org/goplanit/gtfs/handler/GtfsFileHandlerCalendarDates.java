package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsCalendarDate;
import org.goplanit.gtfs.scheme.GtfsCalendarDatesScheme;

/**
 * Base handler for handling calendar dates
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerCalendarDates extends GtfsFileHandler<GtfsCalendarDate> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerCalendarDates() {
    super(new GtfsCalendarDatesScheme());
  }

  /**
   * Handle a GTFS calendar date
   */
  @Override
  public void handle(GtfsCalendarDate gtfsCalendarDate) {
    /* to be implemented by derived class, or ignore */
  }

}
