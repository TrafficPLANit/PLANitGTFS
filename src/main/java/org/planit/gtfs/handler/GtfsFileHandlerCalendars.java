package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsCalendar;
import org.planit.gtfs.scheme.GtfsCalendarsScheme;

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
