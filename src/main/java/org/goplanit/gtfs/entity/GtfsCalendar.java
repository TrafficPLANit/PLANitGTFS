package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in calendar.txt
 * 
 * @author markr
 *
 */
public class GtfsCalendar extends GtfsObject {
  
  /** Supported keys for a GTFS calendar instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.SERVICE_ID,
          GtfsKeyType.MONDAY,
          GtfsKeyType.TUESDAY,
          GtfsKeyType.WEDNESDAY,
          GtfsKeyType.THURSDAY,
          GtfsKeyType.FRIDAY,
          GtfsKeyType.SATURDAY,
          GtfsKeyType.SUNDAY,
          GtfsKeyType.START_DATE,
          GtfsKeyType.END_DATE);

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
    

}
