package org.goplanit.gtfs.enums;

import org.goplanit.utils.enums.EnumOf;
import org.goplanit.utils.enums.EnumValue;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.StringUtils;

import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Defines the different Route Types, i.e., modes:
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
 * @author markr
 */
public enum RouteType implements EnumOf<RouteType,Short>, EnumValue<Short> {
  TRAM_LIGHTRAIL ((short) 0),
  SUBWAY_METRO ((short)1),
  RAIL ((short)2),
  BUS ((short)3),
  FERRY ((short)4),
  CABLE_TRAM ((short) 5),
  AERIAL ((short)6),
  FUNICULAR ((short)7),
  TROLLEY_BUS ((short)11),
  MONO_RAIL ((short)12);


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
