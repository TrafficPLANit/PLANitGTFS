package org.goplanit.gtfs.model;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in calendar_dates.txt
 * 
 * @author markr
 *
 */
public class GtfsCalendarDate extends GtfsObject {
  
  /** Supported keys for a GTFS calendar date instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.SERVICE_ID,
          GtfsKeyType.DATE,
          GtfsKeyType.EXCEPTION_TYPE);

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
    

}
