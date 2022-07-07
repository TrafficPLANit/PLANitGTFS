package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in trips.txt
 * 
 * @author markr
 *
 */
public class GtfsTrip extends GtfsObject {
  
  /** Supported keys for a GTFS trip instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.ROUTE_ID,
          GtfsKeyType.SERVICE_ID,
          GtfsKeyType.TRIP_ID,
          GtfsKeyType.TRIP_HEADSIGN,
          GtfsKeyType.TRIP_SHORT_NAME,
          GtfsKeyType.DIRECTION_ID,
          GtfsKeyType.BLOCK_ID,
          GtfsKeyType.WHEELCHAIR_ACCESSIBLE,
          GtfsKeyType.BIKES_ALLOWED,
          GtfsKeyType.SHAPE_ID);
    
  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
  
  /** Get the trip id
   * 
   * @return trip id
   */
  public String getTripId(){
    return get(GtfsKeyType.TRIP_ID);
  }

  /**
   * String of all key value pairs of this GTFS entity
   * @return created string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("TRIP: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }
}
