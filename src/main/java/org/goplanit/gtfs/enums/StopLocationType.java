package org.goplanit.gtfs.enums;

import org.goplanit.utils.enums.EnumOf;
import org.goplanit.utils.enums.EnumValue;
import org.goplanit.utils.misc.StringUtils;

import java.util.logging.Logger;

/**
 * Defines the different stop location types:
 * <ul>
 *   <li>STOP_PLATFORM (0 or null or ""): A location where passengers board or disembark from a transit vehicle. Is called a platform when defined within a parent_station.</li>
 *   <li>STATION (1): A physical structure or area that contains one or more platform</li>
 *   <li>ENTRANCE_EXIT (2): A location where passengers can enter or exit a station from the street. If an entrance/exit belongs to multiple stations, it can be linked by pathways to both, but the data provider must pick one of them as parent.</li>
 *   <li>GENERIC_NODE (3): A location within a station, not matching any other location_type, which can be used to link together pathways define in pathways.txt.</li>
 *   <li>BOARDING_AREA (4):  A specific location on a platform, where passengers can board and/or alight vehicles </li>
 * </ul>
 *
 * @author markr
 */
public enum StopLocationType implements EnumOf<StopLocationType, Short>, EnumValue<Short> {
  STOP_PLATFORM ((short) 0),
  STATION ((short)1),
  ENTRANCE_EXIT ((short)2),
  GENERIC_NODE ((short)3),
  BOARDING_AREA ((short)4);

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(StopLocationType.class.getCanonicalName());

  private final short value;

  /**
   * Bootstrap to have access to default interface methods
   * @return instance of enum
   */
  private static StopLocationType dummyInstance(){
    return StopLocationType.values()[0];
  }

  /**
   * Constructor
   * @param value numeric value of the type
   */
  StopLocationType(short value){
    this.value = value;
  }

  @Override
  public Short getValue(){
    return value;
  }

  /**
   * Extract the enum based on its internal value (if matched)
   *
   * @param value to base enum on
   * @return found match
   */
  public static StopLocationType of(Short value){
    return dummyInstance().createFromValues(StopLocationType::values,value);
  }

  /**
   * Collect the stop location type belonging to the given value. It is assumed any non-null, non-empty value can be parsed as a short. If not this is logged
   * and null is returned. In case of a blank or null input the location type defaults to STOP_PLATFORM
   *
   * @param value to extract enum for
   * @return the stop location type found, null when not present
   */
  public static StopLocationType parseFrom(String value){
    try{

      if(StringUtils.isNullOrBlank(value)){
        return STOP_PLATFORM;
      }

      return of(Short.valueOf(value));
    }catch (Exception e){
      LOGGER.warning(String.format("Unable to convert %s as short, cannot extract GTFS Stop Location Type",value));
    }
    return null;
  }
}
