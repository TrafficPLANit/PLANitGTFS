package org.planit.gtfs.reader;

import java.net.URL;

import org.planit.gtfs.scheme.GtfsTripsScheme;

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
   * @param gtfsLocation to extract file to parse from
   */
  public GtfsFileReaderTrips(URL gtfsLocation) {
    super(new GtfsTripsScheme(), gtfsLocation);
  }

}
