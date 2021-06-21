package org.planit.gtfs.handler;

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
   * Handle a GTFS trip
   */
  @Override
  public void handle(GtfsTrip gtfsTrip) {
    /* to be implemented by derived class, or ignore */
  }

}
