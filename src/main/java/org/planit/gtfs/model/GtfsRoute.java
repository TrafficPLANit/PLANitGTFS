package org.planit.gtfs.model;

import java.util.EnumSet;

import org.planit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in routes.txt
 * 
 * @author markr
 *
 */
public class GtfsRoute extends GtfsObject {
  
  /** Supported keys for a GTFS route instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.ROUTE_ID,
          GtfsKeyType.AGENCY_ID,
          GtfsKeyType.ROUTE_SHORT_NAME,
          GtfsKeyType.ROUTE_LONG_NAME,
          GtfsKeyType.ROUTE_DESC,
          GtfsKeyType.ROUTE_TYPE,
          GtfsKeyType.ROUTE_URL,
          GtfsKeyType.ROUTE_COLOR,
          GtfsKeyType.ROUTE_TEXT_COLOR,
          GtfsKeyType.ROUTE_SORT_ORDER,
          GtfsKeyType.CONTINUOUS_PICKUP,
          GtfsKeyType.CONTINUOUS_DROP_OFF);
    
  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
  
  /** Get the route id
   * 
   * @return route id
   */
  public String getRouteId(){
    return get(GtfsKeyType.ROUTE_ID);
  }
  
}
