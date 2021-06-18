package org.planit.gtfs.handler;

import java.util.logging.Logger;

import org.planit.gtfs.model.GtfsTrip;
import org.planit.gtfs.scheme.GtfsTripsScheme;

/**
 * Base handler for handling trips
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerTrips extends GtfsFileHandler<GtfsTrip> {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsFileHandlerTrips.class.getCanonicalName());
  
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
    LOGGER.info(gtfsTrip.getTripId());
  }

}
