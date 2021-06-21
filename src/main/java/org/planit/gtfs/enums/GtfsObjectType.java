package org.planit.gtfs.enums;

import org.planit.gtfs.model.*;

/**
 * The available supported object types and their corresponding class. the value represents the in memory class that is used to 
 * represent this GTFS entity.
 * 
 * @author markr
 *
 */
public enum GtfsObjectType {

  AGENCY(GtfsAgency.class),
  CALENDAR(GtfsCalendar.class),
  ROUTE(GtfsRoute.class), 
  STOP(GtfsStop.class),
  STOP_TIME(GtfsStopTime.class),  
  TRIP(GtfsTrip.class); 

  /** value representing the class that goes with the object type enum */
  private final Class<? extends GtfsObject> value;
  
  /** Create a GTFS file type
   * 
   * @param value to use
   */
  private GtfsObjectType(Class<? extends GtfsObject> value){
    this.value = value;
  }
  
  /** Get the value of the enum
   * 
   * @return value of the enum
   */
  public Class<? extends GtfsObject> value() {
    return value;
  }
}

