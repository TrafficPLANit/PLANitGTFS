package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in frequencies.txt
 * 
 * @author markr
 *
 */
public class GtfsFrequency extends GtfsObject {
  
  /** Supported keys for a GTFS frequencies instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.TRIP_ID,
          GtfsKeyType.START_TIME,
          GtfsKeyType.END_TIME,
          GtfsKeyType.HEADWAY_SECS,
          GtfsKeyType.EXACT_TIMES);

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
  
}
