package org.goplanit.gtfs.util;

import org.goplanit.gtfs.converter.GtfsConverterReaderSettingsWithModeMapping;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.mode.ModeFactoryImpl;
import org.goplanit.mode.ModesImpl;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.mode.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Convenience methods usable across various GTFS converter readers
 *
 */
public class GtfsConverterReaderHelper {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(GtfsConverterReaderHelper.class.getCanonicalName());


  /**
   * Based on the settings which define both custom and predefined mappings, we construct the instance mappings from Gtfs mode to PLANit mode, e.g.,
   * when a predefined mode type is configured, it results in an instance of that type on a network, this instance should be available in the passed in
   * #allAvailablePlanitMdes, we then combine these mappings with the custom mode mappings on the settings to create a single consolidated set which can
   * be used during parsing without the need of checking whether a mode is predefined or not.
   *
   * @param settings the settings to obtain the activated two types od mode mappings from
   * @param allAvailablePlanitModes the mode instances used that should be a superset of the activated mode mappings on the reader
   * @return consolidated set of both custom and predefined modes sourced from the allAvailablePlanitModes
   */
  public static HashMap<RouteType, List<Mode>> createCombinedActivatedPlanitModes(
      GtfsConverterReaderSettingsWithModeMapping settings, Modes allAvailablePlanitModes){
    var predefinedModeTypes = settings.getAcivatedPlanitPredefinedModes();

    /* first add predefined modes when present */
    Set<Mode> planitModeInstances = predefinedModeTypes.stream().map( pmt -> allAvailablePlanitModes.get(pmt)).filter(e -> e !=null).collect(Collectors.toSet());

    /* construct mapping, note that a gtfs mode can be mapped to multiple planit modes as well as multiple gtfs modes being mapped to the same planit mode */
    HashMap<RouteType, List<Mode>> gtfsModeToPlanitModeMapping = new HashMap<>();
    var activatedGtfsModes = settings.getAcivatedGtfsModes();
    for(var gtfsMode : activatedGtfsModes) {
      var mappedPredefinedPlanitModes = settings.getAcivatedPlanitPredefinedModes(gtfsMode);
      var availablePlanitModeInstances = planitModeInstances.stream().filter(
          m -> m.isPredefinedModeType() && mappedPredefinedPlanitModes.contains(m.getPredefinedModeType())).collect(Collectors.toList());
      gtfsModeToPlanitModeMapping.put(gtfsMode, availablePlanitModeInstances);
    }

    return gtfsModeToPlanitModeMapping;
  }

  /** add GTFS type Id to PLANit mode external id (in case multiple GTFS modes are mapped to the same PLANit mode)
   *
   * @param planitMode to update external id for
   * @param gtfsMode to use
   */
  public static void addToModeExternalId(Mode planitMode, RouteType gtfsMode){
    if(planitMode != null) {
      String gtfsModeId = String.valueOf(gtfsMode.getValue());
      if(planitMode.hasExternalId() && !planitMode.containsExternalId(';', gtfsModeId)) {
        planitMode.appendExternalId(gtfsModeId, ';');
      }else {
        planitMode.setExternalId(gtfsModeId);
      }
    }
  }

  /**
   * Use when it is the time to make sure that the mapped predefined PLANit modes exist as actual mode instances, so supplement the provides
   * modes container where needed based on the configuration.
   * <p>
   *   Also check based on the type of mode (track type, e.g., water/rail,road) that we only do so, if at least one mode exists
   *   of the same track type. If not log a warning. For example, if we have activate tram, but no rail based mode layers exist
   *   in the network, there is no point in activating, instead log a warning and deactivate on the GTFS settings instead
   * </p>
   *
   * @param settings to base modes configuration on
   * @param network to adjust modes for
   */
  public static void syncActivatedPlanitPredefinedModesBeforeParsing(GtfsServicesReaderSettings settings, MacroscopicNetwork network) {
    /* create temporary mode instances of each mode type */
    var token = IdGroupingToken.create("temp");
    var gtfsActivatedModes = new ModesImpl(token);
    settings.getAcivatedPlanitPredefinedModes().stream().forEach(mt -> gtfsActivatedModes.getFactory().registerNew(mt));

    /* find track types that are supported */
    var supportedTrackTypes = network.getModes().stream().filter( m -> m.hasPhysicalFeatures()).map(m -> m.getPhysicalFeatures().getTrackType()).collect(Collectors.toSet());

    for(var gtfsActivatedMode : gtfsActivatedModes){
      if(supportedTrackTypes.contains(gtfsActivatedMode.getPhysicalFeatures().getTrackType())) {
        network.getModes().getFactory().registerNew(gtfsActivatedMode.getPredefinedModeType());
      }else{
        LOGGER.warning(String.format(
            "DISCARD: Deactivating GTFS mode %s because its track type %s is not present on any mode [%s] in the physical network",
            gtfsActivatedMode.getPredefinedModeType(), gtfsActivatedMode.getPhysicalFeatures().getTrackType(), network.getModes().stream().map(m -> m.getName()).collect(Collectors.joining(","))));
        var gtfsRouteTypesToDeactivate = settings.getAcivatedGtfsModes(gtfsActivatedMode.getPredefinedModeType());
        settings.deactivateGtfsModes(gtfsRouteTypesToDeactivate);
      }
    }

    IdGenerator.reset(token);
  }
}
