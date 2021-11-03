package org.goplanit.gtfs.scheme;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS trips, namely a trips.txt file and a GtfsTrip in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsTripsScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsTripsScheme() {
    super(GtfsFileType.TRIPS, GtfsObjectType.TRIP);
  }

}
