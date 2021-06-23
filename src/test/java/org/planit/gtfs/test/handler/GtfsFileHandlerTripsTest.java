package org.planit.gtfs.test.handler;

import java.util.HashMap;
import java.util.Map;

import org.planit.gtfs.handler.GtfsFileHandlerTrips;
import org.planit.gtfs.model.GtfsTrip;

/**
 * Handler for testing that simply stores the trips in memory
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerTripsTest extends GtfsFileHandlerTrips{

  public final Map<String, GtfsTrip> trips = new HashMap<String, GtfsTrip>();
  
  @Override
  public void handle(GtfsTrip gtfsTrip) {
    trips.put(gtfsTrip.getTripId(), gtfsTrip);
  }

}
