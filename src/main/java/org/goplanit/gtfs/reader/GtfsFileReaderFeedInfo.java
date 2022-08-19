package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsFeedInfoScheme;

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
   */
  protected GtfsFileReaderFeedInfo(URL gtfsLocation) {
    super(new GtfsFeedInfoScheme(), gtfsLocation);
  }

}
