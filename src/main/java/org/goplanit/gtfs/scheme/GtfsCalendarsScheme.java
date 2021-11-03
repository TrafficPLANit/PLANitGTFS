package org.goplanit.gtfs.scheme;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS calendars, namely a calendar.txt file and a GtfsCalendar in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsCalendarsScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsCalendarsScheme() {
    super(GtfsFileType.CALENDARS, GtfsObjectType.CALENDAR);
  }

}
