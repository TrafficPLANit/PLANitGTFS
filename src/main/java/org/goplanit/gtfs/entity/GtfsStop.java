package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.enums.StopLocationType;

/**
 * In memory representation of a GTFS entry in stops.txt.
 * 
 * @author markr
 *
 */
public class GtfsStop extends GtfsObject {
  
  /** Supported keys for a GTFS stop instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.STOP_ID,
          GtfsKeyType.STOP_CODE,
          GtfsKeyType.STOP_NAME,
          GtfsKeyType.STOP_DESC,
          GtfsKeyType.STOP_LAT,
          GtfsKeyType.STOP_LON,
          GtfsKeyType.ZONE_ID,
          GtfsKeyType.STOP_URL,
          GtfsKeyType.LOCATION_TYPE,
          GtfsKeyType.PARENT_STATION,
          GtfsKeyType.STOP_TIMEZONE,
          GtfsKeyType.WHEELCHAIR_BOARDING,
          GtfsKeyType.LEVEL_ID,
          GtfsKeyType.PLATFORM_CODE);

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
  
  /** Get the stop id
   * 
   * @return stop id
   */
  public String getStopId(){
    return get(GtfsKeyType.STOP_ID);
  }

  public String getStopName(){ return get(GtfsKeyType.STOP_NAME); }

  /**
   * Collect as StopLocationType enum directly
   * @return extracted stop location type if valid, null otherwise
   */
  public StopLocationType getLocationType(){
    return StopLocationType.of(getLocationTypeRaw());
  }

  /**
   * Collect raw location type data
   * @return location type value
   */
  public String getLocationTypeRaw(){ return get(GtfsKeyType.LOCATION_TYPE); }

  /**
   * Latitude of the stop location if present
   * @return latitude
   */
  public String getStopLatitude(){ return get(GtfsKeyType.STOP_LAT); }

  /**
   * Longitude of the stop location if present
   * @return latitude
   */
  public String getStopLongitude(){ return get(GtfsKeyType.STOP_LON); }

}
