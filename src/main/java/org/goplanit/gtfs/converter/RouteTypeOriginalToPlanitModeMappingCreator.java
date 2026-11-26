package org.goplanit.gtfs.converter;

import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.utils.mode.PredefinedModeType;

import java.util.List;

/**
 * Maps each GTFS default route type to a PLANit mode, so the memory model's modes are user configurable yet linked to
 * the original format. When the reader is used with a network writer to convert one network to the other, the PLANit
 * modes mapped here must also be mapped by the writer to the output format, so modes are mapped correctly.
 *
 * The default mapping is below. Modes marked N/A have no predefined equivalent in PLANit, so they are ignored.
 *
 * <ul>
 * <li>TRAM_LIGHTRAIL (0) to LightrailMode </li>
 * <li>SUBWAY_METRO (1)   to SubwayMode</li>
 * <li>RAIL (2) to TrainMode</li>
 * <li>BUS (3) to BusMode</li>
 * <li>FERRY (4) to N/A</li>
 * <li>CABLE_TRAM (5) to N/A</li>
 * <li>AERIAL (6) to N/A</li>
 * <li>FUNICULAR (7) to N/A</li>
 * <li>TROLLEY_BUS (8) to BusMode</li>
 * <li>MONO_RAIL (9) to N/A</li>
 * </ul>
 *
 */
public class RouteTypeOriginalToPlanitModeMappingCreator extends RouteTypeToPlanitModeMappingCreator {

  /**
   * Perform and populate mapping in provided settings
   *
   * @param settings to populate
   */
  public static void execute(GtfsConverterReaderSettingsWithModeMapping settings) {

    /* add default mapping for default route types */
    {
      settings.setDefaultGtfs2PredefinedModeTypeMapping(RouteType.TRAM_LIGHTRAIL, List.of(PredefinedModeType.LIGHTRAIL, PredefinedModeType.TRAM));
      settings.setDefaultGtfs2PredefinedModeTypeMapping(RouteType.SUBWAY_METRO, PredefinedModeType.SUBWAY);
      settings.setDefaultGtfs2PredefinedModeTypeMapping(RouteType.RAIL, PredefinedModeType.TRAIN);
      settings.setDefaultGtfs2PredefinedModeTypeMapping(RouteType.BUS, PredefinedModeType.BUS);
      settings.setDefaultGtfs2PredefinedModeTypeMapping(RouteType.TROLLEY_BUS, PredefinedModeType.BUS);
      settings.setDefaultGtfs2PredefinedModeTypeMapping(RouteType.FERRY, PredefinedModeType.FERRY);
    }

    /* activate all mapped defaults initially*/
    {
      settings.activateGtfsRouteTypeMode(RouteType.TRAM_LIGHTRAIL);
      settings.activateGtfsRouteTypeMode(RouteType.SUBWAY_METRO);
      settings.activateGtfsRouteTypeMode(RouteType.RAIL);
      settings.activateGtfsRouteTypeMode(RouteType.BUS);
      settings.activateGtfsRouteTypeMode(RouteType.TROLLEY_BUS);
      settings.activateGtfsRouteTypeMode(RouteType.FERRY);
    }
  }
}
