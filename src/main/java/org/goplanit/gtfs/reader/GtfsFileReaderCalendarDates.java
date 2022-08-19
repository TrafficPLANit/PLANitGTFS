package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsCalendarDatesScheme;

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
   */
  protected GtfsFileReaderCalendarDates(URL gtfsLocation) {
    super(new GtfsCalendarDatesScheme(), gtfsLocation);
  }

}
