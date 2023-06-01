package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsCalendarDatesScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS calendar date entries
 * 
 * @author markr
 *
 */
public class GtfsFileReaderCalendarDates extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderCalendarDates(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsCalendarDatesScheme(), gtfsLocation, filePresenceCondition);
  }

}
