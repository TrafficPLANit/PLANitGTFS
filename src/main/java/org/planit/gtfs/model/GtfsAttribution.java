package org.planit.gtfs.model;

import java.util.EnumSet;

import org.planit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in attributions.txt
 * 
 * @author markr
 *
 */
public class GtfsAttribution extends GtfsObject {
  
  /** Supported keys for a GTFS feed attribution instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.ATTRIBUTION_ID,
          GtfsKeyType.AGENCY_ID,
          GtfsKeyType.ROUTE_ID,
          GtfsKeyType.TRIP_ID,
          GtfsKeyType.ORGANIZATION_NAME,
          GtfsKeyType.IS_PRODUCER,
          GtfsKeyType.IS_OPERATOR,
          GtfsKeyType.IS_AUTHORITY,
          GtfsKeyType.ATTRIBUTION_URL,
          GtfsKeyType.ATTRIBTUION_EMAIL,
          GtfsKeyType.ATTRIBUTION_PHONE);  

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
  
  /** Get the attribution id
   * 
   * @return attribution id
   */
  public String getAttributionId(){
    return get(GtfsKeyType.ATTRIBUTION_ID);
  }   
    

}
