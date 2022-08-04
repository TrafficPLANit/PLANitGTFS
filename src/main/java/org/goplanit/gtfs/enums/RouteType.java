package org.goplanit.gtfs.enums;

import org.goplanit.utils.enums.EnumOf;
import org.goplanit.utils.enums.EnumValue;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.StringUtils;

import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Defines the different (unextended) Route Types, i.e., modes:
 * <ul>
 *   <li>TRAM_LIGHTRAIL (0) - Tram, Streetcar, Light rail. Any light rail or street level system within a metropolitan area.</li>
 *   <li>SUBWAY_METRO (1)   - Subway, Metro. Any underground rail system within a metropolitan area.</li>
 *   <li>RAIL (2)           - Rail. Used for intercity or long-distance travel.</li>
 *   <li>BUS (3)            - Bus. Used for short- and long-distance bus routes.</li>
 *   <li>FERRY (4)          - Ferry. Used for short- and long-distance boat service.</li>
 *   <li>CABLE_TRAM (5)     - Cable tram. Used for street-level rail cars where the cable runs beneath the vehicle, e.g., cable car in San Francisco.</li>
 *   <li>AERIAL LIFT (6)    - Aerial lift, suspended cable car (e.g., gondola lift, aerial tramway). Cable transport where cabins, cars, gondolas or open chairs are suspended by means of one or more cables.</li>
 *   <li>FUNICULAR (7)      - Funicular. Any rail system designed for steep inclines.</li>
 *   <li>TROLLEY_BUS (11)   - Trolley bus. Electric buses that draw power from overhead wires using poles.</li>
 *   <li>MONO_RAIL (12)     - Monorail. Railway in which the track consists of a single rail or a beam.</li>
 * </ul>
 *
 * Further, it also contains the extended route types as well. This allwos us to just use a single enum and given that the numbering does not overlap this is possible.
 * The extended Route types are:
 *
 * <ul>
 *   <li>100-117, 400-405 - Railway Service types</li>
 *   <li>200-209,700-716,800 	- Bus Service types</li>
 *   <li>900-906 	- Tram Service types</li>
 *   <li>1000,1200 	- Water/Ferry Service types</li>
 *   <li>1100 	- Air Service types</li>
 *   <li>1300,1400 	- Aerial/funicular Service types</li>
 *   <li>1500-1507 	- Taxi Service types</li>
 *   <li>1700 	- Misc Service types</li>
 *   <li>1702 	- Horse-drawn carriage Service types</li>
 * </ul>
 *
 * @author markr
 */
public enum RouteType implements EnumOf<RouteType,Short>, EnumValue<Short> {

  /* default route types */

  TRAM_LIGHTRAIL        ((short) 0),
  SUBWAY_METRO          ((short)1),
  RAIL                  ((short)2),
  BUS                   ((short)3),
  FERRY                 ((short)4),
  CABLE_TRAM            ((short) 5),
  AERIAL                ((short)6),
  FUNICULAR             ((short)7),
  TROLLEY_BUS           ((short)11),
  MONO_RAIL             ((short)12),

  /* extended route types */
    /* rail */
  RAILWAY_SERVICE         ((short)100),
  HIGH_SPEED_RAIL_SERVICE ((short)101),
  LONG_DISTANCE_TRAINS    ((short)102),
  INTER_REGIONAL_RAIL_SERVICE ((short)103),
  CAR_TRANSPORT_RAIL_SERVICE ((short)104),
  SLEEPER_RAIL_SERVICE    ((short)105),
  REGIONAL_RAIL_SERVICE   ((short)106),
  TOURIST_RAILWAY_SERVICE ((short)107),
  RAIL_SHUTTLE_IN_COMPLEX ((short)108),
  SUBURBAN_RAILWAY        ((short)109),
  REPLACEMENT_RAIL_SERVICE ((short)110),
  SPECIAL_RAIL_SERVICE    ((short)111),
  LORRY_TRANSPORT_RAIL_SERVICE ((short)112),
  ALL_RAIL_SERVICES       ((short)113),
  CROSS_COUNTRY_RAIL_SERVICE ((short)114),
  VEHICLE_TRANSPORT_RAIL_SERVICE ((short)115),
  RACK_AND_PINION_RAILWAY ((short)116),
  ADDITIONAL_RAIL_SERVICE ((short)117),
    /* coach/bus */
  COACH_SERVICE	          ((short)	200	),
  INTERNATIONAL_COACH_SERVICE	((short)	201	),
  NATIONAL_COACH_SERVICE	((short)	202	),
  SHUTTLE_COACH_SERVICE	  ((short)	203	),
  REGIONAL_COACH_SERVICE	((short)	204	),
  SPECIAL_COACH_SERVICE	  ((short)	205	),
  SIGHTSEEING_COACH_SERVICE	((short)	206	),
  TOURIST_COACH_SERVICE	  ((short)	207	),
  COMMUTER_COACH_SERVICE	((short)	208	),
  ALL_COACH_SERVICES	  ((short)	209	),
    /* other rail */
  URBAN_RAILWAY_SERVICE	((short)	400	),
  METRO_SERVICE	        ((short)	401	),
  UNDERGROUND_SERVICE	((short)	402	),
  URBAN_RAILWAY_SERVICE_ALT	((short)	403	),
  ALL_URBAN_RAILWAY_SERVICES	((short)	404	),
  MONORAIL	((short)	405	),
    /* coach/bus continued */
  BUS_SERVICE	((short)	700	),
  REGIONAL_BUS_SERVICE	((short)	701	),
  EXPRESS_BUS_SERVICE	((short)	702	),
  STOPPING_BUS_SERVICE	((short)	703	),
  LOCAL_BUS_SERVICE	((short)	704	),
  NIGHT_BUS_SERVICE	((short)	705	),
  POST_BUS_SERVICE	((short)	706	),
  SPECIAL_NEEDS_BUS	((short)	707	),
  MOBILITY_BUS_SERVICE	((short)	708	),
  MOBILITY_BUS_FOR_REGISTERED_DISABLED	((short)	709	),
  SIGHTSEEING_BUS	((short)	710	),
  SHUTTLE_BUS	((short)	711	),
  SCHOOL_BUS	((short)	712	),
  SCHOOL_AND_PUBLIC_SERVICE_BUS	((short)	713	),
  RAIL_REPLACEMENT_BUS_SERVICE	((short)	714	),
  DEMAND_AND_RESPONSE_BUS_SERVICE	((short)	715	),
  ALL_BUS_SERVICES	((short)	716	),
  TROLLEYBUS_SERVICE	((short)	800	),
    /* tram */
  TRAM_SERVICE	((short)	900	),
  CITY_TRAM_SERVICE	((short)	901	),
  LOCAL_TRAM_SERVICE	((short)	902	),
  REGIONAL_TRAM_SERVICE	((short)	903	),
  SIGHTSEEING_TRAM_SERVICE	((short)	904	),
  SHUTTLE_TRAM_SERVICE	((short)	905	),
  ALL_TRAM_SERVICES	((short)	906	),
    /* various */
  WATER_TRANSPORT_SERVICE	((short)	1000	),
  AIR_SERVICE	((short)	1100	),
  FERRY_SERVICE	((short)	1200	),
  AERIAL_LIFT_SERVICE	((short)	1300	),
  FUNICULAR_SERVICE	((short)	1400	),
    /* taxi */
  TAXI_SERVICE	((short)	1500	),
  COMMUNAL_TAXI_SERVICE	((short)	1501	),
  WATER_TAXI_SERVICE	((short)	1502	),
  RAIL_TAXI_SERVICE	((short)	1503	),
  BIKE_TAXI_SERVICE	((short)	1504	),
  LICENSED_TAXI_SERVICE	((short)	1505	),
  PRIVATE_HIRE_SERVICE_VEHICLE	((short)	1506	),
  ALL_TAXI_SERVICES	((short)	1507	),
    /* various continued */
  MISCELLANEOUS_SERVICE	((short)	1700	),
  HORSE_DRAWN_CARRIAGE	((short)	1702	);

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(RouteType.class.getCanonicalName());

  private final short value;

  /**
   * Bootstrap to have access to default interface methods
   * @return instance of enum
   */
  private static RouteType dummyInstance(){
    return RouteType.values()[0];
  }

  /**
   * Constructor
   * @param value numeric value of the type
   */
  RouteType(short value){
    this.value = value;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Short getValue(){
    return value;
  }

  /**
   * Create from internal value
   *
   * @param value to base on
   * @return created enum
   */
  public static RouteType of(short value){
    return dummyInstance().createFromValues(RouteType::values,value);
  }

  /**
   * Collect the route type belonging to the given value. It is assumed any non-null, non-empty value can be parsed as a short. If not this is logged
   * and null is returned.
   *
   * @param value to extract enum for
   * @return the stop location type found, null when not present
   */
  public static RouteType parseFrom(String value){
    try{
      return of(Short.valueOf(value));
    }catch (Exception e){
      LOGGER.warning(String.format("Unable to convert %s as short, cannot extract GTFS Stop Location Type",value));
    }
    return null;
  }

}
