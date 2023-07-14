package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsCalendar;
import org.goplanit.gtfs.scheme.GtfsCalendarsScheme;

/**
 * Base handler for handling (service) calendars
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerCalendars extends GtfsFileHandler<GtfsCalendar> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerCalendars() {
    super(new GtfsCalendarsScheme());
  }

  /**
   * Handle a GTFS calendar
   */
  @Override
  public void handle(GtfsCalendar gtfsCalendar) {
    /* to be implemented by derived class, or ignore */
  }

}
