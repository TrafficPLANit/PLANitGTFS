package org.goplanit.gtfs.entity;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.utils.exceptions.PlanItRunTimeException;

/**
 * In memory representation of a GTFS entry in calendar.txt
 * 
 * @author markr
 *
 */
public class GtfsCalendar extends GtfsObject {
  
  /** Supported keys for a GTFS calendar instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.SERVICE_ID,
          GtfsKeyType.MONDAY,
          GtfsKeyType.TUESDAY,
          GtfsKeyType.WEDNESDAY,
          GtfsKeyType.THURSDAY,
          GtfsKeyType.FRIDAY,
          GtfsKeyType.SATURDAY,
          GtfsKeyType.SUNDAY,
          GtfsKeyType.START_DATE,
          GtfsKeyType.END_DATE);

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }

  /**
   * Convert GtfsKeyType to a Java DayOfWeek (if possible)
   * @param gtfsKeyTypeWeekDay to convert
   * @return java DayOfWeek
   */
  public static DayOfWeek asDayOfWeek(GtfsKeyType gtfsKeyTypeWeekDay) throws PlanItRunTimeException{
    switch (gtfsKeyTypeWeekDay){
      case MONDAY:
        return DayOfWeek.MONDAY;
      case TUESDAY:
        return DayOfWeek.TUESDAY;
      case WEDNESDAY:
        return DayOfWeek.WEDNESDAY;
      case THURSDAY:
        return DayOfWeek.THURSDAY;
      case FRIDAY:
        return DayOfWeek.FRIDAY;
      case SATURDAY:
        return DayOfWeek.SATURDAY;
      case SUNDAY:
        return DayOfWeek.SUNDAY;
      default:
        throw new PlanItRunTimeException("Invalid GTFSKeyType %s, unable to convert to day of week", gtfsKeyTypeWeekDay);
    }
  }

  /**
   * Convert Java DayOfWeek to GTFSKeyType (if possible)
   * @param dayOfWeek to convert
   * @return java DayOfWeek
   */
  public static GtfsKeyType asGtfsKeyType(DayOfWeek dayOfWeek) throws PlanItRunTimeException{
    switch (dayOfWeek){
      case MONDAY:
        return GtfsKeyType.MONDAY;
      case TUESDAY:
        return GtfsKeyType.TUESDAY;
      case WEDNESDAY:
        return GtfsKeyType.WEDNESDAY;
      case THURSDAY:
        return GtfsKeyType.THURSDAY;
      case FRIDAY:
        return GtfsKeyType.FRIDAY;
      case SATURDAY:
        return GtfsKeyType.SATURDAY;
      case SUNDAY:
        return GtfsKeyType.SUNDAY;
      default:
        throw new PlanItRunTimeException("Invalid DayOfWeek %s, unable to convert to GTFS Key type", dayOfWeek);
    }
  }

  /**
   * Service id of this instance (need not be a number, so always String)
   *
   * @return service id
   */
  public String getServiceId() {
    return get(GtfsKeyType.SERVICE_ID);
  }

  public boolean isActiveOnMonday(){
    return getMonday()==1;
  }

  public boolean isActiveOnTuesday(){
    return getTuesday()==1;
  }

  public boolean isActiveOnWednesday(){
    return getWednesday()==1;
  }

  public boolean isActiveOnThursday(){
    return getThursday()==1;
  }

  public boolean isActiveOnFriday(){
    return getFriday()==1;
  }

  public boolean isActiveOnSaturday(){
    return getSaturday()==1;
  }

  public boolean isActiveOnSunday(){
    return getSunday()==1;
  }

  public int getMonday(){
    return Integer.parseInt(get(GtfsKeyType.MONDAY));
  }

  public int getTuesday(){
    return Integer.parseInt(get(GtfsKeyType.TUESDAY));
  }

  public int getWednesday(){
    return Integer.parseInt(get(GtfsKeyType.WEDNESDAY));
  }

  public int getThursday(){
    return Integer.parseInt(get(GtfsKeyType.THURSDAY));
  }

  public int getFriday(){
    return Integer.parseInt(get(GtfsKeyType.FRIDAY));
  }

  public int getSaturday(){
    return Integer.parseInt(get(GtfsKeyType.SATURDAY));
  }

  public int getSunday(){
    return Integer.parseInt(get(GtfsKeyType.SUNDAY));
  }

  public boolean isActiveOn(GtfsKeyType gtfsKeyTypeDayOfWeek){
    return Integer.parseInt(get(gtfsKeyTypeDayOfWeek)) == 1;
  }

  /** Verify if day is present on this instance
   *
   * @param dayOfWeek to verify
   * @return true when present, false otherwise
   */
  public boolean isActiveOn(DayOfWeek dayOfWeek){
    return isActiveOn(GtfsCalendar.asGtfsKeyType(dayOfWeek));
  }

  /** Verify if any of the given days exist on this instance
   *
   * @param daysOfWeek to verify
   * @return true when present, false otherwise
   */
  public boolean isActiveOnAny(Collection<DayOfWeek> daysOfWeek){
    return daysOfWeek.stream().filter(e -> isActiveOn(e)).findFirst().isPresent();
  }

  /**
   * String of all key value pairs of this GTFS entity
   * @return created string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("CALENDAR: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }

}
