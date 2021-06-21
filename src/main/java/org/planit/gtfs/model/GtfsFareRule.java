package org.planit.gtfs.model;

import java.util.EnumSet;

import org.planit.gtfs.enums.GtfsKeyType;

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
    

}
