package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsStopsScheme;

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
   */
  public GtfsFileReaderStops(URL gtfsLocation) {
    super(new GtfsStopsScheme(), gtfsLocation);
  }

}
