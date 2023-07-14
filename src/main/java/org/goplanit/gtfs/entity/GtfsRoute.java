package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.utils.misc.StringUtils;

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

  public String getRouteTypeRaw(){ return get(GtfsKeyType.ROUTE_TYPE);}

  public RouteType getRouteType(){ return RouteType.parseFrom(getRouteTypeRaw());}

  public String getShortName(){ return get(GtfsKeyType.ROUTE_SHORT_NAME);}

  public String getLongName(){ return get(GtfsKeyType.ROUTE_LONG_NAME);}

  public String getRouteDescription() {  return get(GtfsKeyType.ROUTE_DESC); }

  /**
   * String of all key value pairs of this GTFS entity
   * @return created string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("ROUTE: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }

  public boolean hasShortName(){
    return !StringUtils.isNullOrBlank(getShortName());
  }

  public boolean hasLongName(){
    return !StringUtils.isNullOrBlank(getLongName());
  }

  public boolean hasRouteDescription() {
    return !StringUtils.isNullOrBlank(getRouteDescription());
  }

  /**
   * Verify if either long or short name is available
   * @return true when valid, false otherwise
   */
  public boolean hasValidName() {
    return hasShortName() || hasLongName();
  }


}
