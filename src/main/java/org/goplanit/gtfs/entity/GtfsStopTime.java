package org.goplanit.gtfs.entity;

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

  /**
   * String of all key value pairs of this GTFS entity
   * @return created string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("STOP_TIME: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }

  public String getTripId() {
    return get( GtfsKeyType.TRIP_ID);
  }

  public String getStopId() {
    return get( GtfsKeyType.STOP_ID);
  }

  public String getArrivalTime() { return get(GtfsKeyType.ARRIVAL_TIME); }

  public String getDepartureTime() { return get(GtfsKeyType.DEPARTURE_TIME); }

  public String getStopSequence() { return get(GtfsKeyType.STOP_SEQUENCE); }

}
