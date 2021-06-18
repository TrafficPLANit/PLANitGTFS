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

  BIKES_ALLOWED("bikes_allowed"),
  BLOCK_ID("block_id"),
  DIRECTION_ID("direction_id"),
  ROUTE_ID("route_id"),
  SERVICE_ID("service_id"),
  SHAPE_ID("shape_id"),
  TRIP_ID("trip_id"),
  TRIP_HEADSIGN("trip_headsign"),
  TRIP_SHORT_NAME("trip_short_name"),    
  WHEELCHAIR_ACCESSIBLE("wheelchair_accessible");  
  

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
