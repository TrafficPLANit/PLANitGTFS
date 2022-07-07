package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in agency.txt
 * 
 * @author markr
 *
 */
public class GtfsAgency extends GtfsObject {
  
  /** Supported keys for a GTFS agency instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.AGENCY_ID,
          GtfsKeyType.AGENCY_NAME,
          GtfsKeyType.AGENCY_URL,
          GtfsKeyType.AGENCY_TIMEZONE,
          GtfsKeyType.AGENCY_LANG,
          GtfsKeyType.AGENCY_FARE_URL,
          GtfsKeyType.AGENCY_PHONE,
          GtfsKeyType.AGENCY_EMAIL);  

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
  
  /** Get the agency id
   * 
   * @return agency id
   */
  public String getAgencyId(){
    return get(GtfsKeyType.AGENCY_ID);
  }

  /**
   * String of all key value pairs of this GTFS entity
   * @return created string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("AGENCY: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }
}
