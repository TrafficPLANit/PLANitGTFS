package org.goplanit.gtfs.converter;

import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;

import java.util.*;
import java.util.stream.Collectors;

import static org.goplanit.gtfs.util.GtfsConverterReaderHelper.createCombinedActivatedPlanitModes;

/**
 * Base class with shared data used by all derived classes
 */
public class GtfsConverterHandlerData {

  /** based on the settings create a single consolidated set of activated PLANit modes combining the predefined mode mappings
   * that materialise as actual mode instances */
  private HashMap<RouteType, List<Mode>> activatedPlanitModesByGtfsMode;

  /** the unique set based on the {@link #activatedPlanitModesByGtfsMode} to avoid having to recreate this on the fly */
  private Set<Mode> activatedPlanitModes;

  /** track the found secondary mode mappings in terms of PLANit modes, i.e., when for some GTFS modes, multiple PLANit modes
   * are mapped, e.g. lightrail and train, then this secondary mapping contains two entries (lightrail,tram) and (tram,lightrail)
   * to allow the algorithm to use this as fallback options when for example no physical path between connectoids can be found based
   * on the primary mapped mode, in which case we can try the other modes, as the infrastructure (stops) might be sourced from elsewhere
   * with a slightly different mode mapping
   */
  private Map<Mode,Set<Mode>> secondaryModeCompatibility;

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
    this.activatedPlanitModes = activatedPlanitModesByGtfsMode.values().stream().flatMap(e -> e.stream()).collect(Collectors.toSet());
    this.settings = settings;


    /* populate the compatible mode mappings */
    {
      List<Pair<Mode,Mode>> temp = new ArrayList<>();
      // create all permutations of pairs that exist (including with itself)
      activatedPlanitModesByGtfsMode.values().forEach(l -> l.forEach(m1 -> l.forEach( m2 -> temp.add(Pair.of(m1,m2)))));
      // now construct all genuine compatible pairs
      for(var entry : temp){
        if(entry.different()) {
          var compatibleModes = secondaryModeCompatibility.get(entry.first());
          if(compatibleModes == null){
            compatibleModes = new HashSet<>(1);
            secondaryModeCompatibility.put(entry.first(), compatibleModes);
          }
          compatibleModes.add(entry.second());
        }
      }
    }
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
  public Mode getPrimaryPlanitModeIfActivated(RouteType gtfsMode){
    var availableModes = activatedPlanitModesByGtfsMode.get(gtfsMode);
    if(availableModes == null || availableModes.isEmpty()){
      return null;
    }
    return availableModes.get(0); // primary mapping is first entry
  }

  /**
   * Collect PLANit modes if it is known as being activated and compatible, otherwise return null
   *
   * @param gtfsMode to check for
   * @return all compatible PLANit modes in order from primary compatible to alternatives that one might consider, null if not present
   */
  public List<Mode> getCompatiblePlanitModesIfActivated(RouteType gtfsMode){
    return Collections.unmodifiableList(activatedPlanitModesByGtfsMode.get(gtfsMode));
  }

  /**
   * Collect compatible PLANit modes from a given PLANit mode (if any). These only exist if a GTFS mode listed more than one
   * mapped PLANit mode, e.g. lightrail and tram, in which case lightrail would return tram and vice versa.
   *
   * @param planitMode to check for
   * @return all compatible PLANit modes
   */
  public Set<Mode> getCompatiblePlanitModesIfActivated(Mode planitMode){
    return Collections.unmodifiableSet(secondaryModeCompatibility.get(planitMode));
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
