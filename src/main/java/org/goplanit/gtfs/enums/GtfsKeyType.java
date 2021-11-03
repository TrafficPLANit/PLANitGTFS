package org.goplanit.gtfs.enums;

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
  ATTRIBTUION_EMAIL("attribution_email"),  
  ATTRIBUTION_ID("attribution_id"), 
  ATTRIBUTION_PHONE("attribution_phone"),
  ATTRIBUTION_URL("attribution_url"),  
  BIKES_ALLOWED("bikes_allowed"),
  BLOCK_ID("block_id"),
  CONTAINS_ID("contains_id"),
  CONTINUOUS_DROP_OFF("continuous_drop_off"), 
  CONTINUOUS_PICKUP("continuous_pickup"),
  CURRENCY_TYPE("currency_type"),
  DATE("date"),
  DEFAULT_LANG("default_lang"),  
  DEPARTURE_TIME("departure_time"),
  DESTINATION_ID("destination_id"),
  DIRECTION_ID("direction_id"),
  DROP_OFF_TYPE("drop_off_type"),
  END_DATE("end_date"),
  END_TIME("end_time"),
  EXACT_TIMES("exact_times"), 
  EXCEPTION_TYPE("exception_type"),
  FARE_ID("fare_id"),
  FIELD_NAME("field_name"),
  FEED_CONTACT_EMAIL("feed_contact_email"), 
  FEED_CONTACT_URL("feed_contact_url"),
  FEED_END_DATE("feed_end_date"),   
  FEED_LANG("feed_lang"),
  FEED_PUBLISHER_NAME("feed_publisher_name"), 
  FEED_PUBLISHER_URL("feed_publisher_url"),
  FEED_START_DATE("feed_start_date"),  
  FEED_VERSION("feed_version"),  
  FIELD_VALUE("field_value"),  
  FRIDAY("friday"),
  FROM_STOP_ID("from_stop_id"), 
  HEADWAY_SECS("headway_secs"),
  IS_AUTHORITY("is_authority"),
  IS_BIDIRECTIONAL("is_bidirectional"),
  IS_PRODUCER("is_producer"), 
  IS_OPERATOR("is_operator"),   
  LANGUAGE("language"),
  LENGTH("length"),
  LEVEL_ID("level_id"),
  LEVEL_INDEX("level_index"), 
  LEVEL_NAME("level_name"),  
  LOCATION_TYPE("location_type"),
  MAX_SLOPE("max_slope"),
  MIN_TRANSFER_TIME("min_transfer_time"),
  MIN_WIDTH("min_width"),
  MONDAY("monday"),
  ORIGIN_ID("origin_id"),
  ORGANIZATION_NAME("organization_name"),
  PARENT_STATION("parent_station"),  
  PATHWAY_ID("pathway_id"),  
  PATHWAY_MODE("pathway_mode"),
  PAYMENT_METHOD("payment_method"), 
  PICKUP_TYPE("pickup_type"),  
  PLATFORM_CODE("platform_code"),
  PRICE("price"),
  RECORD_ID("record_id"),
  RECORD_SUB_ID("record_sub_id"),
  REVERSE_SIGNPOSTED_AS("reversed_signposted_as"),
  ROUTE_COLOR("route_color"), 
  ROUTE_DESC("route_desc"),
  ROUTE_ID("route_id"),
  ROUTE_LONG_NAME("route_long_name"),   
  ROUTE_SHORT_NAME("route_short_name"),
  ROUTE_SORT_ORDER("route_sort_order"),
  ROUTE_TEXT_COLOR("route_text_color"),
  ROUTE_TYPE("route_type"), 
  ROUTE_URL("route_url"),
  SATURDAY("saturday"), 
  SERVICE_ID("service_id"),
  SHAPE_DIST_TRAVELED("shape_dist_traveled"),  
  SHAPE_ID("shape_id"),
  SHAPE_PT_LAT("shape_pt_lat"), 
  SHAPE_PT_LON("shape_pt_lon"), 
  SHAPE_PT_SEQUENCE("shape_pt_sequence"),
  SIGNPOSTED_AS("signposted_as"),
  START_DATE("start_date"),
  START_TIME("start_time"), 
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
  SUNDAY("sunday"),  
  STAIR_COUNT("stair_count"),
  TABLE_NAME("table_name"),
  THURSDAY("thursday"), 
  TIMEPOINT("timepoint"),
  TO_STOP_ID("to_stop_id"),
  TRANSFERS("transfers"),
  TRANSFER_DURATION("transfer_duration"),
  TRANSFER_TYPE("transfer_type"),
  TRANSLATION("translation"),  
  TRAVERSAL_TIME("traversal_time"),
  TUESDAY("tuesday"),  
  TRIP_ID("trip_id"),
  TRIP_HEADSIGN("trip_headsign"),
  TRIP_SHORT_NAME("trip_short_name"),
  WEDNESDAY("wednesday"),
  WHEELCHAIR_ACCESSIBLE("wheelchair_accessible"),
  WHEELCHAIR_BOARDING("wheelchair_boarding"),  
  ZONE_ID("zone_id");    
    
  /** literal column name in GTFS file */
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
