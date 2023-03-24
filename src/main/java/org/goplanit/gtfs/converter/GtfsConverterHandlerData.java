package org.goplanit.gtfs.converter;

import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.mode.Mode;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import static org.goplanit.gtfs.util.GtfsConverterReaderHelper.createCombinedActivatedPlanitModes;

/**
 * Base class with shared data used by all derived classes
 */
public class GtfsConverterHandlerData {

  /** based on the settings create a single consolidated set of activated PLANit modes combining the predefined mode mappings
   * that materialise as actual mode instances */
  private HashMap<RouteType, Mode> activatedPlanitModesByGtfsMode;

  /** the unique set based on the {@link #activatedPlanitModesByGtfsMode} to avoid having to recreate this on the fly */
  private Set<Mode> activatedPlanitModes;

  /** service network to utilise */
  final ServiceNetwork serviceNetwork;

  /* settings with mode mapping */
  final GtfsConverterReaderSettingsWithModeMapping settings;

  /**
   * Constructor
   *
   * @param serviceNetwork to use
   * @param settings to use
   */
  public GtfsConverterHandlerData(final ServiceNetwork serviceNetwork, GtfsConverterReaderSettingsWithModeMapping settings){
    this.serviceNetwork = serviceNetwork;
    this.activatedPlanitModesByGtfsMode = createCombinedActivatedPlanitModes(settings, getServiceNetwork().getParentNetwork().getModes());
    this.activatedPlanitModes = activatedPlanitModesByGtfsMode.values().stream().collect(Collectors.toSet());
    this.settings = settings;
  }

  /** Access to the service network
   * @return the service network being populated
   */
  public ServiceNetwork getServiceNetwork() {
    return serviceNetwork;
  }

  /** activated planit modes, note that initialise should have been called before this is populated
   * @return activated planit mode instances including predefined mode versions
   */
  public Set<Mode> getActivatedPlanitModesByGtfsMode(){
    return Collections.unmodifiableSet(activatedPlanitModes);
  }


  /**
   * Collect PLANit mode if it is known as being activated, otherwise return null
   *
   * @param gtfsMode to check for
   * @return PLANit mode
   */
  public Mode getPlanitModeIfActivated(RouteType gtfsMode){
    return activatedPlanitModesByGtfsMode.get(gtfsMode);
  }

  /**
   * Access to GTFS zoning reader settings
   *
   * @return user configuration settings
   */
  public GtfsConverterReaderSettingsWithModeMapping getSettings() {
    return this.settings;
  }

}
