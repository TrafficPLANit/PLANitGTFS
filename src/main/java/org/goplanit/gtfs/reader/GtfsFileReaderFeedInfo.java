package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsFeedInfoScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS feed info entries
 * 
 * @author markr
 *
 */
public class GtfsFileReaderFeedInfo extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderFeedInfo(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsFeedInfoScheme(), gtfsLocation, filePresenceCondition);
  }

}
