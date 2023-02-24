package org.goplanit.gtfs.converter.service;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettings;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettingsImpl;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.network.layer.service.ServiceNode;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Configurable settings for the Gtfs to PLANit routed services reader
 *
 * @author markr
 *
 */
public class GtfsServicesReaderSettings extends GtfsConverterReaderSettingsImpl implements ConverterReaderSettings {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesReaderSettings.class.getCanonicalName());

  /** filter the GTFS trips by day (or days) of week during parsing, only listed days will be parsed, default, contains all days of week */
  private Set<DayOfWeek> dayOfWeekFilter = DEFAULT_DAYS_OF_WEEK;

  /** Indicates what route types are applied, e.g. the default or the extended */
  private final RouteTypeChoice routeTypeChoice;

  /* configure how to obtain GTFS_STOP_IDs from PLANit service nodes */
  private static final Function<ServiceNode, String> GET_SERVICENODE_TO_GTFS_STOP_ID_FUNCTION = sn -> sn.getExternalId();

  /** Default mapping (specific to this (services) network) from each supported GTFS mode to an available PLANit mode. Not each mapping is necessarily activated.*/
  private  final Map<RouteType, Mode> defaultGtfsMode2PlanitModeMap= new HashMap<>();

  /** Activated GTFS modes. Not all possible mappings might be activated for parsing. */
  private  final Set<RouteType> activatedGtfsModes = new HashSet<>();

  /** allow explicit logging of all trips of a GTFS route by means of its short name */
  private Set<String> logGtfsRouteInformationByShortName = new HashSet<>();

  /**
   * Provides access to how GTFS STOP IDS can be extracted from service nodes when service nodes are created using these settings
   *
   * @return function that maps service node to its GTFS_STOP_ID
   */
  public static Function<ServiceNode, String> getServiceNodeToGtfsStopIdMapping(){
    return GET_SERVICENODE_TO_GTFS_STOP_ID_FUNCTION;
  }

  /**
   * Conduct general initialisation for any instance of this class
   *
   * @param planitModes to populate based on (default) mapping
   */
  protected void initialise(Modes planitModes) {
    switch (routeTypeChoice){
      case EXTENDED:
        RouteTypeExtendedToPlanitModeMappingCreator.execute(this, planitModes);
      case ORIGINAL:
        RouteTypeOriginalToPlanitModeMappingCreator.execute(this, planitModes);
        break;
      default:
        throw new PlanItRunTimeException("Unsupported GTFS route type choice encountered");
    }

    /* ensure external id is set based on GTFS mode */
    getAcivatedGtfsModes().forEach( (gtfsMode) -> addToModeExternalId(getPlanitModeIfActivated(gtfsMode), gtfsMode));
  }


  /** add GTFS type Id to PLANit mode external id (in case multiple GTFS modes are mapped to the same PLANit mode)
   * @param planitMode to update external id for
   * @param gtfsMode to use
   */
  static void addToModeExternalId(Mode planitMode, RouteType gtfsMode){
    if(planitMode != null) {
      String gtfsModeId = String.valueOf(gtfsMode.getValue());
      if(planitMode.hasExternalId()) {
        planitMode.setExternalId(planitMode.getExternalId().concat(";").concat(gtfsModeId));
      }else {
        planitMode.setExternalId(gtfsModeId);
      }
    }
  }

  /** Remove gtfsMode from PLANit external id (in case multiple GTFS modes are mapped to the same PLANit mode)
   *
   * @param planitMode to update external id for
   * @param gtfsMode to use
   */
  static void removeFromModeExternalId(Mode planitMode, RouteType gtfsMode){
    if(planitMode!= null && planitMode.hasExternalId()) {
      String gtfsModeId = String.valueOf(gtfsMode.getValue());
      int startIndex = planitMode.getExternalId().indexOf(gtfsModeId);
      if(startIndex == -1) {
        /* not present */
        return;
      }
      if(startIndex==0) {
        /* first */
        planitMode.setExternalId(planitMode.getExternalId().substring(startIndex+gtfsModeId.length()));
      }else {
        /* not first, so preceded by another" */
        String before = planitMode.getExternalId().substring(0,startIndex-1);
        String after = planitMode.getExternalId().substring(startIndex+gtfsModeId.length());
        planitMode.setExternalId(before.concat(after));
      }
    }
  }

  /** Set mapping from GTFS mode to PLANit mode
   * @param gtfsRouteType to map from
   * @param planitMode mode to map to
   */
  void setDefaultGtfs2PlanitModeMapping(RouteType gtfsRouteType, Mode planitMode) {
    if(gtfsRouteType == null) {
      LOGGER.warning("GTFS mode is null, cannot add it to default PLANit mode mapping, ignored");
      return;
    }
    if(planitMode == null) {
      LOGGER.warning(String.format("PLANit mode is null, cannot add it to default mode mapping, ignored", gtfsRouteType));
      return;
    }
    defaultGtfsMode2PlanitModeMap.put(gtfsRouteType, planitMode);
  }

  /** Activate an GTFS mode, i.e., route type, based on its default mapping to a PLANit mode
   *
   * @param gtfsMode to map from
   */
  void activateGtfsRouteTypeMode(RouteType gtfsMode) {
    if(gtfsMode == null) {
      LOGGER.warning("GTFS mode is null, cannot add it to OSM to PLANit mode mapping for OSM mode, ignored");
      return;
    }
    if(!defaultGtfsMode2PlanitModeMap.containsKey(gtfsMode)){
      LOGGER.warning(String.format("GTFS mode %s has no PLANit mode mapping, cannot activate, ignored", gtfsMode));
      return;
    }
    activatedGtfsModes.add(gtfsMode);
  }

  /** Add/overwrite a mapping from GTFS to PLANit mode. This means that the GTFS Mode will be activated and mapped to the PLANit network
   *
   * @param gtfsMode to set
   * @param planitMode to map it to
   */
  void activateCustomGtfs2PlanitModeMapping(RouteType gtfsMode, Mode planitMode) {
    setDefaultGtfs2PlanitModeMapping(gtfsMode, planitMode);
    activateGtfsRouteTypeMode(gtfsMode);
    addToModeExternalId(planitMode,gtfsMode);
  }

  /** by default all days of week are activated for parsing */
  public static final Set<DayOfWeek> DEFAULT_DAYS_OF_WEEK = Set.of(DayOfWeek.values());


  /** Constructor with user defined source locale
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param routeTypeChoice to apply
   * @param parentNetwork to use
   */
  public GtfsServicesReaderSettings(String inputSource, String countryName, final MacroscopicNetwork parentNetwork, RouteTypeChoice routeTypeChoice) {
    super(inputSource, countryName, parentNetwork);
    this.routeTypeChoice = routeTypeChoice;
    initialise(parentNetwork.getModes());
  }

  /* modes */

  /** Deactivate an OSM mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added.
   *
   * @param gtfsMode to remove
   */
  public void deactivateGtfsMode(RouteType gtfsMode) {
    if(gtfsMode == null) {
      LOGGER.warning("GTFS mode is null, cannot deactivate it, ignored");
      return;
    }
    LOGGER.fine(String.format("GTFS mode %s deactivated", gtfsMode));

    boolean removed = activatedGtfsModes.remove(gtfsMode);
    if(removed) {
      removeFromModeExternalId(defaultGtfsMode2PlanitModeMap.get(gtfsMode), gtfsMode);
    }
  }

  /**Remove all provided GTFS modes from active mapping
   *
   * @param GtfsModes to deactivate
   */
  public void deactivateGtfsModes(Collection<RouteType> GtfsModes) {
    for(RouteType gtfsMode : GtfsModes) {
      deactivateGtfsMode(gtfsMode);
    }
  }

  /** remove all GTFS modes from mapping except for the passed in ones
   *
   * @param remainingGtfsModes to explicitly keep from the GTFSModesToRemove
   */
  public void deactivateAllModesExcept(final List<RouteType> remainingGtfsModes) {
    Collection<RouteType> toBeRemovedModes = List.of(RouteType.values());
    Collection<RouteType> remainingRoadModes = remainingGtfsModes==null ? new ArrayList<>() : remainingGtfsModes;
    var finalToBeRemovedModes = new TreeSet<>(toBeRemovedModes);
    finalToBeRemovedModes.removeAll(remainingRoadModes);
    deactivateGtfsModes(finalToBeRemovedModes);
  }

  /** Convenience method that collects the currently mapped PLANit mode for the given GTFS mode if any
   *
   * @param gtfsMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public Mode getPlanitModeIfActivated(final RouteType gtfsMode) {
    return this.activatedGtfsModes.contains(gtfsMode) ? defaultGtfsMode2PlanitModeMap.get(gtfsMode) : null;
  }

  /** Convenience method that provides access to all the currently active GTFS modes (unmodifiable)
   *
   * @return mapped GTFS modes, if not available (due to lack of mapping or inactive parser) empty collection is returned
   */
  public Collection<RouteType> getAcivatedGtfsModes() {
    return Collections.unmodifiableCollection(activatedGtfsModes);
  }

  /** Convenience method that collects all the currently mapped GTFS modes for the given PLANit mode
   *
   * @param planitMode to collect mapped mode for (if any)
   * @return mapped GTFS modes, if not available (due to lack of mapping or inactive parser) empty collection is returned
   */
  public Collection<RouteType> getAcivatedGtfsModes(final Mode planitMode) {
    Set<RouteType> mappedGtfsModes = new HashSet<>();
    for( RouteType gtfsMode : activatedGtfsModes) {
      if( getPlanitModeIfActivated(gtfsMode).idEquals(planitMode)) {
        mappedGtfsModes.add(gtfsMode);
      }
    }
    return mappedGtfsModes;
  }

  /**
   * Currently activated mapped PLANit modes as a new set, i.e., modifying this set does not impact the configuration
   *
   * @return activated, i.e., mapped PLANit modes
   */
  public Set<Mode> getAcivatedPlanitModes() {
    return activatedGtfsModes.stream().map(gtfsMode -> defaultGtfsMode2PlanitModeMap.get(gtfsMode)).collect(Collectors.toSet());
  }

  /**
   * The route type choice used for identifying the GTFS route modes and mapping them to PLANit modes
   * @return chosen route type choice
   */
  public RouteTypeChoice getRouteTypeChoice(){
    return this.routeTypeChoice;
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
   * @returns true, when tracked, false otherwise
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

  /**
   * Filter GTFS by days of week. If none are set, defaults to no filter and all days of week are processed, the provided
   * days are the days that we keep, missing days are days that will be discarded.
   *
   * @param daysOfWeek to filter on (one or more)
   */
  public void filterDaysOfWeek(DayOfWeek... daysOfWeek){
    if(dayOfWeekFilter == null || dayOfWeekFilter.isEmpty()){
      LOGGER.warning("days of week filter contains no days, defaulting to all days");
      this.dayOfWeekFilter = DEFAULT_DAYS_OF_WEEK;
    }else {
      this.dayOfWeekFilter = Set.of(daysOfWeek);
    }
  }

  /** Currently active days of week that we collect GTFS data for
   *
   * @return daysOfWeekFilter
   */
  public Set<DayOfWeek> getFilteredDaysOfWeek(){
    return dayOfWeekFilter;
  }

  /**
   * Log settings used
   */
  public void log() {
    super.log();

    LOGGER.info(String.format("Route type choice set to: %s ", this.routeTypeChoice));
    LOGGER.info(String.format(
        "Days of week filter keeps: %s ",
        this.dayOfWeekFilter.stream().map(dow -> dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH)).collect(Collectors.joining(","))));

    /* mode mappings GTFS -> PLANit */
    for(var entry : defaultGtfsMode2PlanitModeMap.entrySet()){
      if(activatedGtfsModes.contains(entry.getKey())){
        LOGGER.info(String.format("[ACTIVATED] %s --> %s", entry.getKey(), entry.getValue()));
      }else{
        LOGGER.info(String.format("[DEACTIVATED] %s", entry));
      }
    }

    for(var entry : logGtfsRouteInformationByShortName) {
      LOGGER.info(String.format("Tracking GTFS route %s information while parsing", entry));
    }
  }

}
