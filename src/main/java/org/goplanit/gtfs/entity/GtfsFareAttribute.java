package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in fare_attributes.txt
 * 
 * @author markr
 *
 */
public class GtfsFareAttribute extends GtfsObject {
  
  /** Supported keys for a GTFS fare attribute instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.AGENCY_ID,
          GtfsKeyType.CURRENCY_TYPE,
          GtfsKeyType.FARE_ID,
          GtfsKeyType.PAYMENT_METHOD,
          GtfsKeyType.PRICE,          
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
    var sb = new StringBuilder("FARE_ATTRIBUTE: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }
}
