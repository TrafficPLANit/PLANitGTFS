package org.goplanit.gtfs.converter.service;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettings;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.Modes;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Configurable settings for the Gtfs to PLANit routed services reader
 *
 * @author markr
 *
 */
public class GtfsServicesReaderSettings extends GtfsConverterReaderSettings implements ConverterReaderSettings {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesReaderSettings.class.getCanonicalName());

  /** Indicates what route types are applied, e.g. the default or the extended */
  private final RouteTypeChoice routeTypeChoice;

  /** Default mapping (specific to this (services) network) from each supported GTFS mode to an available PLANit mode. Not each mapping is necessarily activated.*/
  private  final Map<RouteType, Mode> defaultGtfsMode2PlanitModeMap= new HashMap<>();

  /** Activated GTFS modes. Not all possible mappings might be activated for parsing. */
  private  final Set<RouteType> activatedGtfsModes = new HashSet<>();

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

  /**
   * Conduct general initialisation for any instance of this class
   *
   * @param planitModes to populate based on (default) mapping
   */
  protected void initialise(Modes planitModes) {
    switch (getRouteTypeChoice()){
      case EXTENDED:
        //TODO: implement the below execute to continue
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

  /**
   * The route type choice used for identifying the GTFS route modes and mapping them to PLANit modes
   * @return chosen route type choice
   */
  public RouteTypeChoice getRouteTypeChoice(){
    return this.routeTypeChoice;
  }

  /* modes */

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
   * Log settings used
   */
  public void log() {
    super.log();

    LOGGER.info(String.format("Route type choice set to: %s ", this.routeTypeChoice));

    /* mode mappings GTFS -> PLANit */
    for(var entry : defaultGtfsMode2PlanitModeMap.entrySet()){
      if(activatedGtfsModes.contains(entry.getKey())){
        LOGGER.info(String.format("[ACTIVATED] %s --> %s", entry.getKey(), entry.getValue()));
      }else{
        LOGGER.info(String.format("[DEACTIVATED] %s", entry));
      }
    }
  }

  /**
   * Currently activated mapped PLANit modes
   * @return activated, i.e., mapped PLANit modes
   */
  public Set<Mode> getAcivatedPlanitModes() {
    return activatedGtfsModes.stream().map(gtfsMode -> defaultGtfsMode2PlanitModeMap.get(gtfsMode)).collect(Collectors.toSet());
  }
}
