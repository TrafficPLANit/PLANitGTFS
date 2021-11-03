package org.goplanit.gtfs.model;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in stop_times.txt
 * 
 * @author markr
 *
 */
public class GtfsStopTime extends GtfsObject {
  
  /** Supported keys for a GTFS stop time instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.TRIP_ID,
          GtfsKeyType.ARRIVAL_TIME,
          GtfsKeyType.DEPARTURE_TIME,
          GtfsKeyType.STOP_ID,
          GtfsKeyType.STOP_SEQUENCE,
          GtfsKeyType.STOP_HEADSIGN,
          GtfsKeyType.PICKUP_TYPE,
          GtfsKeyType.DROP_OFF_TYPE,
          GtfsKeyType.CONTINUOUS_PICKUP,
          GtfsKeyType.CONTINUOUS_DROP_OFF,
          GtfsKeyType.SHAPE_DIST_TRAVELED,
          GtfsKeyType.TIMEPOINT);

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
    

}
