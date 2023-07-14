package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in fare_rules.txt
 * 
 * @author markr
 *
 */
public class GtfsFareRule extends GtfsObject {
  
  /** Supported keys for a GTFS fare rule instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.FARE_ID,
          GtfsKeyType.ROUTE_ID,
          GtfsKeyType.ORIGIN_ID,
          GtfsKeyType.DESTINATION_ID,
          GtfsKeyType.CONTAINS_ID,          
          GtfsKeyType.TRANSFERS,          
          GtfsKeyType.TRANSFER_DURATION);  

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
    var sb = new StringBuilder("FARE_RULE: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }
}
