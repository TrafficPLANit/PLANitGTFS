package org.planit.gtfs.reader;

import org.planit.gtfs.model.GtfsTrip;
import org.planit.gtfs.scheme.GtfsTripsScheme;

/**
 * Base handler for handling trips
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerTrips extends GtfsFileHandler<GtfsTrip> {

  /**
   * Constructor
   */
  public GtfsFileHandlerTrips() {
    super(new GtfsTripsScheme());
  }

  /**
   * Handle a Gtfs trip
   */
  @Override
  public void handle(GtfsTrip gtfsTrip) {
    // do nothing let user override derived class method
  }

}
