package org.goplanit.gtfs.converter.service;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettingsWithModeMapping;
import org.goplanit.gtfs.converter.RouteTypeExtendedToPredefinedPlanitModeMappingCreator;
import org.goplanit.gtfs.converter.RouteTypeOriginalToPlanitModeMappingCreator;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.ComparablePair;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.network.layer.service.ServiceNode;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Configurable settings for the Gtfs to PLANit routed services reader
 *
 * @author markr
 *
 */
public class GtfsServicesReaderSettings extends GtfsConverterReaderSettingsWithModeMapping implements ConverterReaderSettings {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesReaderSettings.class.getCanonicalName());

  /* configure how to obtain GTFS_STOP_IDs from PLANit service nodes */
  private static final Function<ServiceNode, String> GET_SERVICENODE_TO_GTFS_STOP_ID_FUNCTION = sn -> sn.getExternalId();

  /** when true all GTFS trips which are identical except for their departure time will be grouped into a single PLANitTripSchedule, when false they are kept separate */
  private boolean groupIdenticalGtfsTrips = DEFAULT_GROUP_IDENTICAL_GTFS_TRIPS;

  /** currently the GTFS parser will only generate PLANit services and service network based on a single reference day provided. If multiple are required
   * the parser needs to be run multiple times.
   *
   * todo: allow additional functionality to generate multiple results in one go
   */
  private DayOfWeek dayOfWeek;

  /** configured activated time periods, if empty, all are supported implicitly*/
  private final Set<ComparablePair<LocalTime, LocalTime>> timePeriodFilters;

  /** when non-empty we exclude ALL GTFS routes except the ones registered */
  private final Set<String> exceptionsToBlanketBlackListByShortName = new TreeSet<>();

  /** when non-empty we exclude the provided GTFS routes from parsing */
  private final Set<String> excludeGtfsRoutesByShortName = new HashSet<>();

  /** flag allowing users to include partial trips from the moment their stop times enter the eligible time period filter despite the trip departure time
   * falling outside this window */
  private  boolean includePartialGtfsTripsWithInvalidDepartureIfStopsInTimePeriod = DEFAULT_INCLUDE_PARTIAL_GTFS_TRIPS_IF_STOPS_IN_TIME_PERIOD;

  /** allow explicit logging of all trips of a GTFS route by means of its short name */
  private Set<String> logGtfsRouteInformationByShortName = new HashSet<>();

  /**
   * Log the routes found to use the GTFS stop (for the time periods that are chosen)
   */
  private final Set<String> logGtfsStopRoutes = new HashSet<>();

  /**
   * Conduct general initialisation for any instance of this class
   *
   */
  protected void initialiseDefaultModeMappings() {

    switch (routeTypeChoice){
      case EXTENDED:
        RouteTypeExtendedToPredefinedPlanitModeMappingCreator.execute(this);
      case ORIGINAL:
        RouteTypeOriginalToPlanitModeMappingCreator.execute(this);
        break;
      default:
        throw new PlanItRunTimeException("Unsupported GTFS route type choice encountered");
    }

  }

  /**
   * Validate the settings, log issues found
   *
   * @return false if not valid
   */
  boolean validate() {
    if(getDayOfWeek() == null){
      LOGGER.severe("Day of week not chosen for GTFS services reader settings, unable to continue");
      return false;
    }

    return true;
  }

  /** by default group all identical Gtfs trips in a single PLANit trip (with a departure listing per original Gtfs trip) */
  public static final boolean DEFAULT_GROUP_IDENTICAL_GTFS_TRIPS = true;

  /** by default, we include GTFS trips from the moment a stop falls within the eligible time period */
  public static final boolean DEFAULT_INCLUDE_PARTIAL_GTFS_TRIPS_IF_STOPS_IN_TIME_PERIOD = true;

  /**
   * Provides access to how GTFS STOP IDS can be extracted from service nodes when service nodes are created using these settings
   *
   * @return function that maps service node to its GTFS_STOP_ID
   */
  public static Function<ServiceNode, String> getServiceNodeToGtfsStopIdMapping(){
    return GET_SERVICENODE_TO_GTFS_STOP_ID_FUNCTION;
  }

  /** Constructor with user defined source locale, the input source and other settings are reuqired to be populated by
   * the user afterwards
   *
   * @param countryName to base source locale on
   */
  public GtfsServicesReaderSettings(String countryName) {
    this((URL) null, countryName, null, RouteTypeChoice.EXTENDED);
  }

  /** Constructor with user defined source locale, input location and route choice type info
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param routeTypeChoice to apply
   */
  public GtfsServicesReaderSettings(String inputSource, String countryName, RouteTypeChoice routeTypeChoice) {
    this(inputSource, countryName, null, routeTypeChoice);
  }

  /** Constructor with user defined source locale
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param dayOfWeekFilter to use
   * @param routeTypeChoice to apply
   */
  public GtfsServicesReaderSettings(String inputSource, String countryName, DayOfWeek dayOfWeekFilter, RouteTypeChoice routeTypeChoice) {
    this( (URL) (inputSource==null ? null : UrlUtils.createFrom(inputSource)), countryName, dayOfWeekFilter, routeTypeChoice);
  }

  /** Constructor with user defined source locale
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param dayOfWeekFilter to use
   * @param routeTypeChoice to apply
   */
  public GtfsServicesReaderSettings(URL inputSource, String countryName, DayOfWeek dayOfWeekFilter, RouteTypeChoice routeTypeChoice) {
    super(inputSource, countryName, routeTypeChoice);
    this.dayOfWeek = dayOfWeekFilter;
    this.timePeriodFilters = new TreeSet<>();
  }

  /**
   * Exclude all GTFS routes, except the ones provided here. Overrides any previous calls to this method, so only the provided
   * listing is considered
   *
   * @param gtfsShortNames the only routes not to exclude
   */
  public void excludeAllGtfsRoutesExceptByShortName(String... gtfsShortNames){
    excludeAllGtfsRoutesExceptByShortName(Arrays.asList(gtfsShortNames));
  }

  /**
   * Exclude all GTFS routes, except the ones provided here. Overrides any previous calls to this method, so only the provided
   * listing is considered
   *
   * @param gtfsShortNames the only routes not to exclude
   */
  public void excludeAllGtfsRoutesExceptByShortName(List<String> gtfsShortNames){
    exceptionsToBlanketBlackListByShortName.addAll(gtfsShortNames);
  }

  /**
   * Verify if a GTFS route is included
   *
   * @param gtfsShortName to verify
   * @return true when included, false otherwise
   */
  public boolean isGtfsRouteIncludedByShortName(String gtfsShortName){
    return !excludeGtfsRoutesByShortName.contains(gtfsShortName) &&
        (exceptionsToBlanketBlackListByShortName.isEmpty() || exceptionsToBlanketBlackListByShortName.contains(gtfsShortName));
  }

  /**
   * Exclude all GTFS routes, except the ones provided here. Overrides any previous calls to this method, so only the provided
   * listing is considered
   *
   * @param gtfsShortNames the only routes not to exclude
   */
  public void excludeGtfsRoutesByShortName(String... gtfsShortNames){
    excludeGtfsRoutesByShortName(Arrays.asList(gtfsShortNames));
  }

  /**
   * Exclude all GTFS routes, except the ones provided here. Overrides any previous calls to this method, so only the provided
   * listing is considered
   *
   * @param gtfsShortNames the only routes not to exclude
   */
  public void excludeGtfsRoutesByShortName(List<String> gtfsShortNames){
    excludeGtfsRoutesByShortName.addAll(gtfsShortNames);
  }


  /**
   * Activate tracking of information for a given GTFS route by its short name
   *
   * @param gtfsShortName to trac while parsing
   */
  public void activateLoggingForGtfsRouteByShortName(String gtfsShortName){
    logGtfsRouteInformationByShortName.add(gtfsShortName);
  }

  /**
   * Verify if a GTFS short name is already tracked for logging purposes
   *
   * @param gtfsShortName to verify
   * @return true, when tracked, false otherwise
   */
  public boolean isActivatedLoggingForGtfsRouteByShortName(String gtfsShortName){
    return logGtfsRouteInformationByShortName.contains(gtfsShortName);
  }

  /**
   * Unmodifiable set of tracked GTFS routes for logging purposes
   *
   * @return the set
   */
  public Set<String> getActivatedLoggingForGtfsRoutesByShortName(){
    return Collections.unmodifiableSet(logGtfsRouteInformationByShortName);
  }

  /** Add a time period filter. When one or more filters are set, not full day of chosen day of week is parsed but only the trips that
   * departure within the registered time periods
   *
   * @param startTimeWithinDay within day start time (inclusive)
   * @param endTimeWithinDay within day end time (inclusive)
   */
  public void addTimePeriodFilter(LocalTime startTimeWithinDay, LocalTime endTimeWithinDay) {
    var newEntry = ComparablePair.of(startTimeWithinDay, endTimeWithinDay);
    boolean overlap = timePeriodFilters.stream().anyMatch(
        e -> !startTimeWithinDay.isBefore(e.first()) && !startTimeWithinDay.isAfter(e.second()) ||
        !endTimeWithinDay.isBefore(e.first()) && !endTimeWithinDay.isAfter(e.second()));
    if(overlap){
      LOGGER.warning(String.format("[DISCARD] overlapping time period filter, consider revising filter (%s to %s)",
          startTimeWithinDay.format(DateTimeFormatter.ISO_LOCAL_TIME), endTimeWithinDay.format(DateTimeFormatter.ISO_LOCAL_TIME)));
      return;
    }

    timePeriodFilters.add(newEntry);
  }

  /**
   * Collect the currently configured time period filters. If no filters are applied, an empty set is provided, representing
   * all times are included
   *
   * @return map of eligible time periods by start, end time pairs
   */
  public Set<Pair<LocalTime, LocalTime>> getTimePeriodFilters() {
    return Collections.unmodifiableSet(timePeriodFilters);
  }

  /**
   * Check whether any time periods are being filtered
   *
   * @return true when filters are set, false otherwise
   */
  public boolean hasTimePeriodFilters() {
    return getTimePeriodFilters()!=null && !getTimePeriodFilters().isEmpty();
  }

  /**
   * Set the day of week to filter on (mandatory to be set)
   *
   * @param dayOfWeek to choose
   */
  public void setDayOfWeek(DayOfWeek dayOfWeek) {
    this.dayOfWeek = dayOfWeek;
  }

  /**
   * The day of week to filter on
   * @return dayOfWeek chosen
   */
  public DayOfWeek getDayOfWeek() {
    return this.dayOfWeek;
  }

  /**
   * Log settings used
   */
  public void logSettings() {
    super.logSettings();

    LOGGER.info(String.format("Activated day of week: %s", dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)));

    if(hasTimePeriodFilters()) {
      LOGGER.info("Activated time periods:");
      getTimePeriodFilters().forEach( e -> LOGGER.info(
          String.format("Start-time: %s End-time: %s",
              e.first().format(DateTimeFormatter.ISO_LOCAL_TIME),
              e.second().format(DateTimeFormatter.ISO_LOCAL_TIME))));

    }else{
      LOGGER.info("Activated time periods: ALL - NO FILTER");
    }

    if(!exceptionsToBlanketBlackListByShortName.isEmpty()){
      LOGGER.info(String.format("Filtering GTFS routes to only include: %s", String.join(",", exceptionsToBlanketBlackListByShortName)));
    }

    LOGGER.info(String.format("Consolidate identical GTFS trips: %s ", String.valueOf(isGroupIdenticalGtfsTrips())));
    LOGGER.info(String.format("Including partial GTFS trips for portion within time period: %s ", String.valueOf(isIncludePartialGtfsTripsIfStopsInTimePeriod())));

    for(var entry : logGtfsRouteInformationByShortName) {
      LOGGER.info(String.format("Tracking GTFS route %s information while parsing", entry));
    }
  }

  /** check value of flag
   *
   * @return flag
   */
  public boolean isGroupIdenticalGtfsTrips() {
    return groupIdenticalGtfsTrips;
  }

  /**
   * Set flag indicating to group GtfsTrips into single PLANit Trip schedule as long as they are identical except for departure time (which is listed separately)
   * @param groupIdenticalGtfsTrips flag to set
   */
  public void setGroupIdenticalGtfsTrips(boolean groupIdenticalGtfsTrips) {
    this.groupIdenticalGtfsTrips = groupIdenticalGtfsTrips;
  }

  /** get flag allowing users to include partial trips from the moment their stop times enter the eligible time period filter despite the trip departure time
   * falling outside this window
   *
   * @return flag state
   */
  public boolean isIncludePartialGtfsTripsIfStopsInTimePeriod() {
    return includePartialGtfsTripsWithInvalidDepartureIfStopsInTimePeriod;
  }

  /** set flag allowing users to include partial trips from the moment their stop times enter the eligible time period filter despite the trip departure time
   * falling outside this window
   *
   * @param includePartialGtfsTripsIfStopsInTimePeriod flag to set
   */
  public void setIncludePartialGtfsTripsIfStopsInTimePeriod(boolean includePartialGtfsTripsIfStopsInTimePeriod) {
    this.includePartialGtfsTripsWithInvalidDepartureIfStopsInTimePeriod = includePartialGtfsTripsIfStopsInTimePeriod;
  }

  /**
   * Indicate to log the routes that stop at the given GTFS stops (within selected time period(s). Can be useful
   * for debugging purposes.
   *
   * @param gtfsStopIds GTFS stop id to provide stopping GTFS routes for
   */
  public void addLogGtfsStopRoutes(final String... gtfsStopIds) {
    addLogGtfsStopRoutes(Arrays.asList(gtfsStopIds));
  }

  /**
   * Indicate to log the routes that stop at the given GTFS stops (within selected time period(s). Can be useful
   * for debugging purposes.
   *
   * @param gtfsStopIds GTFS stop id to provide stopping GTFS routes for
   */
  public void addLogGtfsStopRoutes(final List<String> gtfsStopIds) {
    logGtfsStopRoutes.addAll(gtfsStopIds);
  }

  /** Verify if GTFS stop id is marked for logging the stopping GTFS routes for
   *
   * @param gtfsStopId GTFS stop id to verify
   * @return true when present, false otherwise
   */
  public boolean isLogGtfsStopRoute(final String gtfsStopId) {
    return logGtfsStopRoutes.contains(gtfsStopId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    this.timePeriodFilters.clear();
    this.dayOfWeek = null;
    this.logGtfsStopRoutes.clear();
    this.excludeGtfsRoutesByShortName.clear();
    this.exceptionsToBlanketBlackListByShortName.clear();
  }

}
