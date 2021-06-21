package org.planit.gtfs.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

/**
 * List of all available GTFS keys used in GTFS files
 * 
 * @author markr
 *
 */
public enum GtfsKeyType {

  AGENCY_ID("agency_id"),
  AGENCY_EMAIL("agency_email"),
  AGENCY_FARE_URL("agency_fare_url"),
  AGENCY_LANG("agency_lang"), 
  AGENCY_NAME("agency_name"),
  AGENCY_PHONE("agency_phone"), 
  AGENCY_TIMEZONE("agency_timezone"),
  AGENCY_URL("agency_url"),
  ARRIVAL_TIME("arrival_time"),   
  BIKES_ALLOWED("bikes_allowed"),
  BLOCK_ID("block_id"),
  CONTINUOUS_DROP_OFF("continuous_drop_off"), 
  CONTINUOUS_PICKUP("continuous_pickup"),
  DEPARTURE_TIME("departure_time"),  
  DIRECTION_ID("direction_id"),
  DROP_OFF_TYPE("drop_off_type"),  
  LEVEL_ID("level_id"),
  LOCATION_TYPE("location_type"),
  PARENT_STATION("parent_station"),
  PICKUP_TYPE("pickup_type"),  
  PLATFORM_CODE("platform_code"),
  ROUTE_COLOR("route_color"), 
  ROUTE_DESC("route_desc"),
  ROUTE_ID("route_id"),
  ROUTE_LONG_NAME("route_long_name"),   
  ROUTE_SHORT_NAME("route_short_name"),
  ROUTE_SORT_ORDER("route_sort_order"),
  ROUTE_TEXT_COLOR("route_text_color"),
  ROUTE_TYPE("route_type"), 
  ROUTE_URL("route_url"),    
  SERVICE_ID("service_id"),
  SHAPE_DIST_TRAVELED("shape_dist_traveled"),  
  SHAPE_ID("shape_id"),
  STOP_CODE("stop_code"),
  STOP_DESC("stop_desc"),
  STOP_HEADSIGN("stop_headsign"),   
  STOP_ID("stop_id"),  
  STOP_LAT("stop_lat"), 
  STOP_LON("stop_lon"), 
  STOP_NAME("stop_name"),
  STOP_SEQUENCE("stop_sequence"),  
  STOP_TIMEZONE("stop_timezone"), 
  STOP_URL("stop_url"),
  TIMEPOINT("timepoint"),    
  TRIP_ID("trip_id"),
  TRIP_HEADSIGN("trip_headsign"),
  TRIP_SHORT_NAME("trip_short_name"),    
  WHEELCHAIR_ACCESSIBLE("wheelchair_accessible"),
  WHEELCHAIR_BOARDING("wheelchair_boarding"),  
  ZONE_ID("zone_id"), 
  MONDAY("monday"), 
  TUESDAY("tuesday"), 
  WEDNESDAY("wednesday"), 
  THURSDAY("thursday"), 
  FRIDAY("friday"), 
  SATURDAY("saturday"), 
  SUNDAY("sunday"), 
  START_DATE("start_date"), 
  END_DATE("end_date"); 
   
  private final String value;
  
  /** Create a GTFS key type
   * 
   * @param value to use
   */
  private GtfsKeyType(String value){
    this.value = value;
  }
  
  /** Get the value of the enum
   * 
   * @return value of the enum
   */
  public String value() {
    return value;
  }

  /** Verify if value is present in provided enumset's values 
   * 
   * @param supportedKeys to check
   * @param value to check
   * @return true when present, false otherwise
   */
  public static boolean valueIn(EnumSet<GtfsKeyType> supportedKeys, String value) {
    return supportedKeys.stream().anyMatch( entry -> entry.value.equals(value));
  }

  /** Construct enum from given value
   * 
   * @param value
   * @return GtfsKeyType
   */
  public static Optional<GtfsKeyType> fromValue(String value) {
    return Arrays.stream(values()).filter( key -> key.value.equals(value)).findFirst();
  }
  
}
