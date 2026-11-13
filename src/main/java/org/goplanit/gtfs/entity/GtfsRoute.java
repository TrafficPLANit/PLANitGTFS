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

  /**
   * Collect the route type as given in the feed
   *
   * @return route type as given, null when absent
   */
  public String getRouteTypeRaw(){ return get(GtfsKeyType.ROUTE_TYPE);}

  /**
   * Collect the route type, parsed from the value given in the feed, see {@link RouteType#parseFrom}
   *
   * @return route type
   */
  public RouteType getRouteType(){ return RouteType.parseFrom(getRouteTypeRaw());}

  /**
   * Collect the route's short name
   *
   * @return short name, null when absent
   */
  public String getShortName(){ return get(GtfsKeyType.ROUTE_SHORT_NAME);}

  /**
   * Collect the route's long name
   *
   * @return long name, null when absent
   */
  public String getLongName(){ return get(GtfsKeyType.ROUTE_LONG_NAME);}

  /**
   * Collect the route's description
   *
   * @return description, null when absent
   */
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

  /**
   * Verify if the route has a short name that is not blank
   *
   * @return true when it has one, false otherwise
   */
  public boolean hasShortName(){
    return !StringUtils.isNullOrBlank(getShortName());
  }

  /**
   * Verify if the route has a long name that is not blank
   *
   * @return true when it has one, false otherwise
   */
  public boolean hasLongName(){
    return !StringUtils.isNullOrBlank(getLongName());
  }

  /**
   * Verify if the route has a description that is not blank
   *
   * @return true when it has one, false otherwise
   */
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
