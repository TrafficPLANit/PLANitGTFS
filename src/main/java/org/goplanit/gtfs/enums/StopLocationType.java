package org.goplanit.gtfs.enums;

import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Defines the different sop location types:
 * <li>
 *   <ul>STOP_PLATFORM (0): A location where passengers board or disembark from a transit vehicle. Is called a platform when defined within a parent_station.</ul>
 *   <ul>STATION (1): A physical structure or area that contains one or more platform</ul>
 *   <ul>ENTRANCE_EXIT (2): A location where passengers can enter or exit a station from the street. If an entrance/exit belongs to multiple stations, it can be linked by pathways to both, but the data provider must pick one of them as parent.</ul>
 *   <ul>GENERIC_NODE (3): A location within a station, not matching any other location_type, which can be used to link together pathways define in pathways.txt.</ul>
 *   <ul>BOARDING_AREA (4):  A specific location on a platform, where passengers can board and/or alight vehicles </ul>
 * </li>
 *
 * @author markr
 */
public enum StopLocationType {
  STOP_PLATFORM ((short) 0),
  STATION ((short)1),
  ENTRANCE_EXIT ((short)2),
  GENERIC_NODE ((short)3),
  BOARDING_AREA ((short)4);

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(StopLocationType.class.getCanonicalName());

  private final short value;

  /**
   * Constructor
   * @param value numeric value of the type
   */
  StopLocationType(short value){
    this.value = value;
  }

  public short getValue(){
    return value;
  }

  /**
   * Collect the stop location type belonging to the given value
   * @param value to extract enum for
   * @return the stop location type found, null when not present
   */
  public static StopLocationType of(short value){
    var values = StopLocationType.values();
    for(int index = 0 ; index < values.length; ++index){
      if( values[index].value == value){
        return values[index];
      }
    }
    return null;
  }

  /**
   * Collect the stop location type belonging to the given value. It is assumed the value can be parsed as a short. If not this is logged
   * and null is returned.
   *
   * @param value to extract enum for
   * @return the stop location type found, null when not present
   */
  public static StopLocationType of(String value){
    try{
      return of(Short.valueOf(value));
    }catch (Exception e){
      LOGGER.warning(String.format("Unable to parse %s as short, cannot extract GTFS Stop Location Type",value));
    }
    return null;
  }
}
