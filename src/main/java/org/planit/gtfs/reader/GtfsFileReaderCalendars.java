package org.planit.gtfs.reader;

import java.net.URL;

import org.planit.gtfs.scheme.GtfsCalendarsScheme;

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
  public GtfsFileReaderCalendars(URL gtfsLocation) {
    super(new GtfsCalendarsScheme(), gtfsLocation);
  }

}
