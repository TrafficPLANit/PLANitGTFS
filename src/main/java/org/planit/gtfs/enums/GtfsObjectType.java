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
  ATTRIBUTION(GtfsAttribution.class),
  CALENDAR(GtfsCalendar.class),
  CALENDAR_DATE(GtfsCalendarDate.class),
  FARE_ATTRIBUTE(GtfsFareAttribute.class),
  FARE_RULE(GtfsFareRule.class),
  FEED_INFO(GtfsFeedInfo.class),
  FREQUENCY(GtfsFrequency.class),
  LEVEL(GtfsLevel.class),
  PATHWAY(GtfsPathway.class),
  ROUTE(GtfsRoute.class),
  SHAPE(GtfsShape.class),
  STOP(GtfsStop.class),
  STOP_TIME(GtfsStopTime.class),
  TRANSFER(GtfsTransfer.class),
  TRANSLATION(GtfsTranslation.class),
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

