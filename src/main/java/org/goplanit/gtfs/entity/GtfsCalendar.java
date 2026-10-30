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

  /**
   * Verify if the service runs on Mondays
   *
   * @return true when active on Mondays, false otherwise
   */
  public boolean isActiveOnMonday(){
    return getMonday()==1;
  }

  /**
   * Verify if the service runs on Tuesdays
   *
   * @return true when active on Tuesdays, false otherwise
   */
  public boolean isActiveOnTuesday(){
    return getTuesday()==1;
  }

  /**
   * Verify if the service runs on Wednesdays
   *
   * @return true when active on Wednesdays, false otherwise
   */
  public boolean isActiveOnWednesday(){
    return getWednesday()==1;
  }

  /**
   * Verify if the service runs on Thursdays
   *
   * @return true when active on Thursdays, false otherwise
   */
  public boolean isActiveOnThursday(){
    return getThursday()==1;
  }

  /**
   * Verify if the service runs on Fridays
   *
   * @return true when active on Fridays, false otherwise
   */
  public boolean isActiveOnFriday(){
    return getFriday()==1;
  }

  /**
   * Verify if the service runs on Saturdays
   *
   * @return true when active on Saturdays, false otherwise
   */
  public boolean isActiveOnSaturday(){
    return getSaturday()==1;
  }

  /**
   * Verify if the service runs on Sundays
   *
   * @return true when active on Sundays, false otherwise
   */
  public boolean isActiveOnSunday(){
    return getSunday()==1;
  }

  /**
   * Collect the Monday flag as given in the feed: 1 when the service runs on Mondays, 0 otherwise
   *
   * @return Monday flag
   */
  public int getMonday(){
    return Integer.parseInt(get(GtfsKeyType.MONDAY));
  }

  /**
   * Collect the Tuesday flag as given in the feed: 1 when the service runs on Tuesdays, 0 otherwise
   *
   * @return Tuesday flag
   */
  public int getTuesday(){
    return Integer.parseInt(get(GtfsKeyType.TUESDAY));
  }

  /**
   * Collect the Wednesday flag as given in the feed: 1 when the service runs on Wednesdays, 0 otherwise
   *
   * @return Wednesday flag
   */
  public int getWednesday(){
    return Integer.parseInt(get(GtfsKeyType.WEDNESDAY));
  }

  /**
   * Collect the Thursday flag as given in the feed: 1 when the service runs on Thursdays, 0 otherwise
   *
   * @return Thursday flag
   */
  public int getThursday(){
    return Integer.parseInt(get(GtfsKeyType.THURSDAY));
  }

  /**
   * Collect the Friday flag as given in the feed: 1 when the service runs on Fridays, 0 otherwise
   *
   * @return Friday flag
   */
  public int getFriday(){
    return Integer.parseInt(get(GtfsKeyType.FRIDAY));
  }

  /**
   * Collect the Saturday flag as given in the feed: 1 when the service runs on Saturdays, 0 otherwise
   *
   * @return Saturday flag
   */
  public int getSaturday(){
    return Integer.parseInt(get(GtfsKeyType.SATURDAY));
  }

  /**
   * Collect the Sunday flag as given in the feed: 1 when the service runs on Sundays, 0 otherwise
   *
   * @return Sunday flag
   */
  public int getSunday(){
    return Integer.parseInt(get(GtfsKeyType.SUNDAY));
  }

  /**
   * Verify if the service runs on the given day of the week
   *
   * @param gtfsKeyTypeDayOfWeek key of the day, e.g. {@link GtfsKeyType#MONDAY}
   * @return true when active on that day, false otherwise
   */
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
