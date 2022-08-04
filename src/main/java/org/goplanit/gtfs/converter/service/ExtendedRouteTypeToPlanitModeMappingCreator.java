package org.goplanit.gtfs.converter.service;

import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.mode.PredefinedModeType;

/**
 * Each eligible GTFS extended Route type is mapped to a PLANit mode through this dedicated mapping class so that the memory model's modes
 * are user configurable yet linked to the original format. Note that when the reader is used
 * i.c.w. a network writer to convert one network to the other. It is paramount that the PLANit modes
 * that are mapped here are also mapped by the writer to the output format to ensure a correct I/O mapping of modes
 *
 * The default extended mapping is provided below. It is important to realise that modes that are marked as N/A have no predefined
 * equivalent in PLANit, as a result they are ignored.
 *
 * <ul>
 *   <li>100-117, 400-405 - Railway Service types to TrainMode</li>
 *   <li>200-209,700-716,800 	- Bus Service types to BusMode </li>
 *   <li>900-906 	- Tram Service types to LightrailMode</li>
 *   <li>1000,1200 	- Water/Ferry Service types to N/A </li>
 *   <li>1100 	- Air Service types to N/A</li>
 *   <li>1300,1400 	- Aerial/funicular Service types to N/A</li>
 *   <li>1500-1507 	- Taxi Service types to N/A</li>
 *   <li>1700 	- Misc Service typesto N/A </li>
 *   <li>1702 	- Horse-drawn carriage Service types to N/A</li>
 * </ul>
 *
 */
public class ExtendedRouteTypeToPlanitModeMappingCreator {

  /**
   * Perform and populate mapping in provided settings
   *
   * @param settings to populate
   * @param planitModes to use
   */
  public static void execute(GtfsServicesReaderSettings settings, Modes planitModes) {
    /* initialise road modes on planit side that we are about to map */
    {
      //TODO continue here
//      planitModes.getFactory().registerNew(PredefinedModeType.LIGHTRAIL);
//      planitModes.getFactory().registerNew(PredefinedModeType.SUBWAY);
//      planitModes.getFactory().registerNew(PredefinedModeType.TRAIN);
//      planitModes.getFactory().registerNew(PredefinedModeType.BUS);
    }

    /* add default mapping for default route types */
    {
      //TODO continue here
//      settings.setDefaultGtfs2PlanitModeMapping(RouteType.TRAM_LIGHTRAIL, planitModes.get(PredefinedModeType.LIGHTRAIL));
//      settings.setDefaultGtfs2PlanitModeMapping(RouteType.SUBWAY_METRO, planitModes.get(PredefinedModeType.SUBWAY));
//      settings.setDefaultGtfs2PlanitModeMapping(RouteType.RAIL, planitModes.get(PredefinedModeType.TRAIN));
//      settings.setDefaultGtfs2PlanitModeMapping(RouteType.BUS, planitModes.get(PredefinedModeType.BUS));
//      settings.setDefaultGtfs2PlanitModeMapping(RouteType.TROLLEY_BUS, planitModes.get(PredefinedModeType.BUS));
    }

    /* activate all mapped defaults initially*/
    {
      //TODO continue here
//      settings.activateGtfsRouteTypeMode(RouteType.TRAM_LIGHTRAIL);
//      settings.activateGtfsRouteTypeMode(RouteType.SUBWAY_METRO);
//      settings.activateGtfsRouteTypeMode(RouteType.RAIL);
//      settings.activateGtfsRouteTypeMode(RouteType.BUS);
//      settings.activateGtfsRouteTypeMode(RouteType.TROLLEY_BUS);
    }
  }
}
