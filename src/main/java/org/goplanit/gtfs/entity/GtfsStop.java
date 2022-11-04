package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.enums.StopLocationType;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

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
   * @return true when stop name is not null or blank, false otherwise
   */
  public boolean hasStopName(){ return !StringUtils.isNullOrBlank(getStopName()); }

  /** Check for populated platform code
  * @return true when present false otherwise
  */
  public boolean hasPlatformCode(){ return !StringUtils.isNullOrBlank(getPlatformCode()); }

  /**
   * Collect platform code
   * @return platform code
   */
  public String getPlatformCode(){ return get(GtfsKeyType.PLATFORM_CODE); }

  /**
   * Collect as StopLocationType enum directly
   * @return extracted stop location type if valid, null otherwise
   */
  public StopLocationType getLocationType(){
    return StopLocationType.parseFrom(getLocationTypeRaw());
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

  /**
   * Collect long (x), lat (y) as JTS coordinate
   *
   * @return coordinate
   */
  public Coordinate getLocationAsCoord(){
    return new Coordinate(Double.valueOf(getStopLongitude()), Double.valueOf(getStopLatitude()));
  }

  /**
   * Collect long (x), lat (y) as JTS Point
   *
   * @return point
   */
  public Point getLocationAsPoint(){
    try {
      return PlanitJtsUtils.createPoint(getLocationAsCoord());
    }catch(Exception e){
      throw new PlanItRunTimeException("Unable to transform geometry of GTFS stop %s to PLANit network CRS", getStopId());
    }
  }

  /**
   * String of all key value pairs of this GTFS entity
   * @return string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("STOP: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }

}
