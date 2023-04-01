package org.goplanit.gtfs.converter;

import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.containers.ContainerUtils;
import org.goplanit.utils.containers.ListUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
      secondaryModeCompatibility = new HashMap<>();
      // create all permutations of pairs that exist (including with itself)
      for(var planitModesForGtfsMode : activatedPlanitModesByGtfsMode.values()){
        if(planitModesForGtfsMode.size()<=1){
          continue;
        }
        /* if the permutations exist for any gtfs mode, then we allow it for all (assuming this is done consistently, but
         * avoiding being too strict, e.g., if one gtfs mode maps to planit mode 2 and 3, and another to 3 and 4, we assume 2,3, and 4
         * are all compatible */
        var currModePairPermutations = ListUtils.getPairPermutations(planitModesForGtfsMode, false);
        for(var entry : currModePairPermutations){
          var result = secondaryModeCompatibility.getOrDefault(entry.first(), new HashSet<>());
          result.add(entry.second());
          secondaryModeCompatibility.put(entry.first(), result);
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
   * Collect PLANit modes if it is known as being activated and compatible (unmodifiable), otherwise return null
   *
   * @param gtfsMode to check for
   * @return all compatible PLANit modes in order from primary compatible to alternatives that one might consider, null if not present
   */
  public List<Mode> getCompatiblePlanitModesIfActivated(RouteType gtfsMode){
    return ContainerUtils.wrapInUnmodifiableListUnlessNull(activatedPlanitModesByGtfsMode.get(gtfsMode));
  }

  /**
   * Collect compatible PLANit modes from a given PLANit mode (if any). These only exist if a GTFS mode listed more than one
   * mapped PLANit mode, e.g. lightrail and tram, in which case lightrail would return tram and vice versa.
   *
   * @param planitMode to check for
   * @return all compatible PLANit modes (unmodifiable)
   */
  public Set<Mode> getCompatiblePlanitModesIfActivated(Mode planitMode){
    return ContainerUtils.wrapInUnmodifiableSetUnlessNull(secondaryModeCompatibility.get(planitMode));
  }

  /**
   * Expand the mode to all compatible modes (if any) including the mode itself
   *
   * @param planitMode to expand
   * @return original mode supplemented with any compatible modes
   */
  public Set<Mode> expandWithCompatibleModes(Mode planitMode){
    var compatibleAltModes = secondaryModeCompatibility.get(planitMode);
    if(compatibleAltModes == null){
      return new HashSet<>(Collections.singleton(planitMode));
    }
    return Stream.concat(compatibleAltModes.stream(), Stream.of(planitMode)).collect(Collectors.toSet());
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
