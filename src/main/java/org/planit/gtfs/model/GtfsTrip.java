package org.planit.gtfs.model;

import java.util.EnumSet;

import org.planit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in trips.txt
 * 
 * @author markr
 *
 */
public class GtfsTrip extends GtfsObject {
  
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
  
  public String getTripId(){
    return get(GtfsKeyType.TRIP_ID);
  }
  
}
