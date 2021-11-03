package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsStopTimesScheme;

/**
 * A GTFS file reader for parsing GTFS stop times
 * 
 * @author markr
 *
 */
public class GtfsFileReaderStopTimes extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  public GtfsFileReaderStopTimes(URL gtfsLocation) {
    super(new GtfsStopTimesScheme(), gtfsLocation);
  }

}
