package org.goplanit.gtfs.converter.service;

import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.mode.PredefinedModeType;

/**
 * Base class with helper methods to convert route types to PLANit modes. to be derived from for actual usage
 *
 */
abstract class RouteTypeToPlanitModeMappingCreator {

  /** Make sure the correct modes are available for mapping
   *
   * @param planitModes to register on
   */
  protected static void registerPlanitModes(Modes planitModes){
      planitModes.getFactory().registerNew(PredefinedModeType.LIGHTRAIL);
      planitModes.getFactory().registerNew(PredefinedModeType.SUBWAY);
      planitModes.getFactory().registerNew(PredefinedModeType.TRAIN);
      planitModes.getFactory().registerNew(PredefinedModeType.BUS);
  }
}
