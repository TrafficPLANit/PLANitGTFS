package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsCalendarsScheme;

/**
 * A GTFS file reader for parsing GTFS calendar entries
 * 
 * @author markr
 *
 */
public class GtfsFileReaderCalendars extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  protected GtfsFileReaderCalendars(URL gtfsLocation) {
    super(new GtfsCalendarsScheme(), gtfsLocation);
  }

}
