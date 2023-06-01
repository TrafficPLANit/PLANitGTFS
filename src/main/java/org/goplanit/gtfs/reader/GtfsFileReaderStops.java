package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsStopsScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS stops
 * 
 * @author markr
 *
 */
public class GtfsFileReaderStops extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderStops(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsStopsScheme(), gtfsLocation, filePresenceCondition);
  }

}
