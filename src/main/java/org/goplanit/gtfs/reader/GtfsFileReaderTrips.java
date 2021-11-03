package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsTripsScheme;

/**
 * A GTFS file reader for parsing GTFS trips
 * 
 * @author markr
 *
 */
public class GtfsFileReaderTrips extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  public GtfsFileReaderTrips(URL gtfsLocation) {
    super(new GtfsTripsScheme(), gtfsLocation);
  }

}
