package org.planit.gtfs.reader;

import java.net.URL;

import org.planit.gtfs.scheme.GtfsStopsScheme;

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
