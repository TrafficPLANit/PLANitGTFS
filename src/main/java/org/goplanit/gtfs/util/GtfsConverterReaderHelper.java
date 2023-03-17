package org.goplanit.gtfs.util;

import org.goplanit.gtfs.converter.GtfsConverterReaderSettingsWithModeMapping;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.mode.PredefinedMode;

import java.util.HashMap;
import java.util.Map;
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
  public static HashMap<RouteType, Mode> createCombinedActivatedPlanitModes(GtfsConverterReaderSettingsWithModeMapping settings, Modes allAvailablePlanitModes){
    var customModes =settings.getAcivatedPlanitCustomModes();
    var predefinedModeTypes = settings.getAcivatedPlanitPredefinedModes();

    /* first add predefined modes when present */
    Set<Mode> planitModeInstances = predefinedModeTypes.stream().map( pmt -> allAvailablePlanitModes.get(pmt)).filter(e -> e !=null).collect(Collectors.toSet());
    /* now add custom modes */
    planitModeInstances.addAll(customModes);
    if(planitModeInstances.size() != customModes.size() + predefinedModeTypes.size()){
      LOGGER.warning("modes available do not contain all mapped GTFS modes, consider verifying if mode mapping is correct");
    }

    /* construct mapping */
    HashMap<RouteType, Mode> gtfsModeToPlanitModeMapping = new HashMap<>();
    for(var mode : planitModeInstances){
      if(mode.isPredefinedModeType()){
        settings.findGtfsModesFor(mode.getPredefinedModeType()).forEach(gtfsMode -> gtfsModeToPlanitModeMapping.put(gtfsMode, mode));
      }else{
        settings.findGtfsModesFor(mode).forEach( gtfsMode -> gtfsModeToPlanitModeMapping.put(gtfsMode, mode));
      }
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
   * modes container where needed based on the configuration
   *
   * @param settings to base modes configuration on
   * @param modes to adjust
   */
  public static void updatePlanitModesBeforeParsing(GtfsServicesReaderSettings settings, Modes modes) {
    settings.getAcivatedPlanitPredefinedModes().forEach( modeType -> modes.getFactory().registerNew(modeType));

    var activatedCustomModes = settings.getAcivatedPlanitCustomModes();
    var missingActivatedPlanitModes = activatedCustomModes.stream().filter(customMode -> !modes.containsValue(customMode)).collect(Collectors.toList());
    if(!missingActivatedPlanitModes.isEmpty()){
      LOGGER.severe(String.format("Some PLANit modes were activated on GTFS reader, but seem not available: [%s]", missingActivatedPlanitModes.stream().map(m -> m.toString()).collect(Collectors.joining(","))));
    }
    activatedCustomModes.retainAll(missingActivatedPlanitModes);

    /* ensure external id is added if not present based on mapped GTFS modes */
    activatedCustomModes.forEach( customMode ->settings.findGtfsModesFor(customMode).forEach( gtfsMode -> addToModeExternalId(customMode, gtfsMode)));
  }
}
