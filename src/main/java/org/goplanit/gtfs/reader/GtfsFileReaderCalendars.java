package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsCalendarsScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

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
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderCalendars(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsCalendarsScheme(), gtfsLocation, filePresenceCondition);
  }

}
