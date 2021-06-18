package org.planit.gtfs.reader;

import org.planit.gtfs.scheme.GtfsTripsScheme;

/**
 * A GTFS file reader for parsing GTFS trips
 * 
 * @author markr
 *
 */
public class GtfsFileReaderTrips extends GtfsFileReaderBase {

  /**
   * Default constructor
   */
  public GtfsFileReaderTrips() {
    super(new GtfsTripsScheme());
  }

}
