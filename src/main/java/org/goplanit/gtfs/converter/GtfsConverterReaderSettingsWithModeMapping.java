package org.goplanit.gtfs.converter;

import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Capture the mode mapping aspect of GTFS converter readers for raw (static) GTFS feeds. To be
 * used as base class not as actual settings class to be exposed.
 * 
 * @author markr
 *
 */
public class GtfsConverterReaderSettingsWithModeMapping extends GtfsConverterReaderSettingsImpl {

    /** Logger to use */
    private static final Logger LOGGER = Logger.getLogger(GtfsConverterReaderSettingsWithModeMapping.class.getCanonicalName());

    /** Indicates what route types are applied, e.g. the default or the extended */
    protected final RouteTypeChoice routeTypeChoice;

    /** Default mapping (specific to this (services) network) from each supported GTFS mode to PLANit predefined mode. Not each mapping is necessarily activated.*/
    protected  final Map<RouteType, PredefinedModeType> defaultGtfsMode2PrefinedModeTypeMap;

    /** USer configured additional mapping from a supported GTFS mode to PLANit non-predefined mode. Each mapping is expected to be activated, otherwise it is ignored.*/
    protected  final Map<RouteType, Mode> gtfsMode2CustomModeMap;

    /** Activated GTFS modes. Not all possible mappings might be activated for parsing. */
    protected  final Set<RouteType> activatedGtfsModes;

    /**
     * Find the GTFS mode that is mapped to the given mode representation
     *
     * @param <T> way the mode is represented and mapped to a GTFS mode
     * @param modeMapping to collect Gtfs route types for
     * @return found mappings
     */
    private <T> Set<RouteType> findGtfsModesFor(Map<RouteType, T> modeTypeMap, T modeMapping) {
      Set<RouteType> routeTypes = new HashSet<>();
      for(var entry : modeTypeMap.entrySet()){
        if(entry.getValue().equals(modeMapping) && isGtfsModeActivated(entry.getKey())){
          routeTypes.add(entry.getKey());
        }
      }
      return routeTypes;
    }

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


    /** Set mapping from GTFS mode to PLANit mode
     * @param gtfsRouteType to map from
     * @param planitMode mode type to map to
     */
    protected void setDefaultGtfs2PredefinedModeTypeMapping(RouteType gtfsRouteType, PredefinedModeType planitMode) {
      if(gtfsRouteType == null) {
        LOGGER.warning("GTFS mode is null, cannot add it to default PLANit mode mapping, ignored");
        return;
      }
      if(planitMode == PredefinedModeType.CUSTOM) {
        LOGGER.warning(String.format("PLANit mode is not a predefined type, cannot add it to default mode mapping, ignored", gtfsRouteType));
        return;
      }
      defaultGtfsMode2PrefinedModeTypeMap.put(gtfsRouteType, planitMode);
    }

    /** Set mapping from GTFS mode to PLANit mode
     * @param gtfsRouteType to map from
     * @param planitMode mode to map to
     */
    protected void setGtfs2CustomPlanitModeMapping(RouteType gtfsRouteType, Mode planitMode) {
      if(gtfsRouteType == null) {
        LOGGER.warning("GTFS mode is null, cannot add it to default PLANit mode mapping, ignored");
        return;
      }
      if(planitMode == null) {
        LOGGER.warning(String.format("Custom PLANit mode is null, cannot add it to mode mapping, ignored", gtfsRouteType));
        return;
      }
      gtfsMode2CustomModeMap.put(gtfsRouteType, planitMode);
    }

    /** Activate an GTFS mode, i.e., route type, based on its default mapping to a PLANit mode
     *
     * @param gtfsMode to map from
     */
    protected void activateGtfsRouteTypeMode(RouteType gtfsMode) {
      if(gtfsMode == null) {
        LOGGER.warning("GTFS mode is null, cannot add it to Gtfs to PLANit mode mapping, ignored");
        return;
      }
      if(!defaultGtfsMode2PrefinedModeTypeMap.containsKey(gtfsMode)){
        LOGGER.warning(String.format("GTFS mode %s has no PLANit predefined mode type mapping, cannot activate, ignored", gtfsMode));
        return;
      }
      activatedGtfsModes.add(gtfsMode);
    }

    /** Add/overwrite a mapping from GTFS to custom PLANit mode. This means that the GTFS Mode will be activated and mapped to the PLANit network
     *
     * @param gtfsMode to set
     * @param planitMode to map it to
     */
    protected void activateCustomGtfs2PlanitModeMapping(RouteType gtfsMode, Mode planitMode) {
      setGtfs2CustomPlanitModeMapping(gtfsMode, planitMode);
      activateGtfsRouteTypeMode(gtfsMode);
    }

    /** Constructor with user defined source locale
     *
     * @param inputSource to use
     * @param countryName to base source locale on
     * @param routeTypeChoice to apply
     */
    protected GtfsConverterReaderSettingsWithModeMapping(String inputSource, String countryName, RouteTypeChoice routeTypeChoice) {
      super(inputSource, countryName);
      this.routeTypeChoice = routeTypeChoice;

      this.activatedGtfsModes = new HashSet<>();
      this.defaultGtfsMode2PrefinedModeTypeMap = new HashMap<>();
      this.gtfsMode2CustomModeMap = new HashMap<>();

      initialiseDefaultModeMappings();
    }

  /** Copy constructor (reference copy because we use it to share the same mode mapping across multiple instance)
   *  todo not great because technically this is a hack and we do not create a shallow copy but simply assign references...
   *
   * @param other to use
   */
  protected GtfsConverterReaderSettingsWithModeMapping(GtfsConverterReaderSettingsWithModeMapping other) {
    super(other.getInputDirectory(), other.getCountryName());
    this.routeTypeChoice = other.getRouteTypeChoice();

    // reference assignment
    this.activatedGtfsModes = other.activatedGtfsModes;
    this.defaultGtfsMode2PrefinedModeTypeMap = other.defaultGtfsMode2PrefinedModeTypeMap;
    this.gtfsMode2CustomModeMap = other.gtfsMode2CustomModeMap;
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
      activatedGtfsModes.remove(gtfsMode);
    }

    /**
     * Verify if Gtfs Mode has been activated or not
     *
     * @param gtfsMode to verify
     * @return true when activate, false otherwise
     */
    public boolean isGtfsModeActivated(RouteType gtfsMode){
      return activatedGtfsModes.contains(gtfsMode);
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

    /** Convenience method that provides access to all the currently active GTFS modes (unmodifiable)
     *
     * @return mapped GTFS modes, if not available (due to lack of mapping or inactive parser) empty collection is returned
     */
    public Collection<RouteType> getAcivatedGtfsModes() {
      return Collections.unmodifiableCollection(activatedGtfsModes);
    }

    /**
     * Currently activated mapped PLANit modes as a new set, i.e., modifying this set does not impact the configuration
     *
     * @return activated, i.e., mapped PLANit predefined mode types
     */
    public Set<PredefinedModeType> getAcivatedPlanitPredefinedModes() {
      return activatedGtfsModes.stream().map(gtfsMode -> defaultGtfsMode2PrefinedModeTypeMap.get(gtfsMode)).filter( e -> e != null).collect(Collectors.toSet());
    }

    /**
     * Currently activated mapped custom PLANit modes as a new set, i.e., modifying this set does not impact the configuration
     *
     * @return activated, i.e., mapped PLANit modes
     */
    public Set<Mode> getAcivatedPlanitCustomModes() {
      return activatedGtfsModes.stream().map(gtfsMode -> gtfsMode2CustomModeMap.get(gtfsMode)).filter( e -> e != null).collect(Collectors.toSet());
    }

    /**
     * Find the GTFS mode that is mapped to the given custom mode
     * @param customMode to collect Gtfs route types for
     * @return found mappings
     */
    public Set<RouteType> findGtfsModesFor(Mode customMode) {
      return findGtfsModesFor(gtfsMode2CustomModeMap, customMode);
    }

    /**
     * Find the GTFS mode that is mapped to the given predefined PLANit mode
     * @param predefinedModeType to collect Gtfs route types for
     * @return found mappings
     */
    public Set<RouteType> findGtfsModesFor(PredefinedModeType predefinedModeType) {
      return findGtfsModesFor(defaultGtfsMode2PrefinedModeTypeMap, predefinedModeType);
    }

    /**
     * The route type choice used for identifying the GTFS route modes and mapping them to PLANit modes
     * @return chosen route type choice
     */
    public RouteTypeChoice getRouteTypeChoice(){
      return this.routeTypeChoice;
    }

    /**
     * Log settings used
     */
    public void logSettings() {
      super.logSettings();

      LOGGER.info(String.format("Route type choice set to: %s ", this.routeTypeChoice));

      /* mode mappings GTFS -> PLANit */
      for(var entry : defaultGtfsMode2PrefinedModeTypeMap.entrySet()){
        if(activatedGtfsModes.contains(entry.getKey())){
          LOGGER.info(String.format("[ACTIVATED] %s --> %s", entry.getKey(), entry.getValue()));
        }else{
          LOGGER.info(String.format("[DEACTIVATED] %s", entry));
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
      super.reset();
      // todo reset mode mapping
    }

  }
