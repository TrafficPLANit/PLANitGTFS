package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

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
