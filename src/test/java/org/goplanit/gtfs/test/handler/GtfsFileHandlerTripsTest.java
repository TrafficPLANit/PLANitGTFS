package org.goplanit.gtfs.test.handler;

import java.util.HashMap;
import java.util.Map;

import org.goplanit.gtfs.handler.GtfsFileHandlerTrips;
import org.goplanit.gtfs.entity.GtfsTrip;

/**
 * Handler for testing that simply stores the trips in memory
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerTripsTest extends GtfsFileHandlerTrips{

  public final Map<String, GtfsTrip> trips = new HashMap<>();
  
  @Override
  public void handle(GtfsTrip gtfsTrip) {
    trips.put(gtfsTrip.getTripId(), gtfsTrip);
  }

}
