package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS calendar dates, namely a calendar_dates.txt file and a GtfsCalendarDate in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsCalendarDatesScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsCalendarDatesScheme() {
    super(GtfsFileType.CALENDAR_DATES, GtfsObjectType.CALENDAR_DATE);
  }

}
