package org.goplanit.gtfs.converter;

import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.utils.mode.PredefinedModeType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *   <li>1700 	- Misc Service types to N/A </li>
 *   <li>1702 	- Horse-drawn carriage Service types to N/A</li>
 * </ul>
 *
 */
public class RouteTypeExtendedToPredefinedPlanitModeMappingCreator extends RouteTypeToPlanitModeMappingCreator {

  /**
   * Perform and populate mapping in provided settings
   *
   * @param settings to populate
   */
  public static void execute(GtfsConverterReaderSettingsWithModeMapping settings) {
    /* add default mapping for default route types */
    Map<PredefinedModeType, Set<RouteType>> modeMappings = new HashMap<>();
    // allow multi mapping where first entry is preferred and second, third, etc. are fallbacks indicating
    // the mode is considered compatible with stops/infrastructure servicing the secondary modes as well in case no
    // matches can be found based on primary match
    Map<RouteType, List<PredefinedModeType>> gtfs2PlanitModeMappings = new HashMap<>();
    {
      /* train types */
      var trainTypes = RouteType.getInValueRange((short)100,(short)117);
      trainTypes.add(RouteType.of((short)400));
      trainTypes.addAll(RouteType.getInValueRange((short)403,(short)404));
      trainTypes.add(RouteType.of((short)1503));
      trainTypes.forEach( gtfsModeType -> gtfs2PlanitModeMappings.put(gtfsModeType, List.of(PredefinedModeType.TRAIN)));
      modeMappings.put(PredefinedModeType.TRAIN, trainTypes);

      /* bus types */
      var busTypes = RouteType.getInValueRange((short)200,(short)209);
      busTypes.addAll(RouteType.getInValueRange((short)700,(short)716));
      busTypes.add(RouteType.of((short)800));
      busTypes.forEach( gtfsModeType -> gtfs2PlanitModeMappings.put(gtfsModeType, List.of(PredefinedModeType.BUS)));
      modeMappings.put(PredefinedModeType.BUS, busTypes);

      /* subway/metro types */
      modeMappings.put(PredefinedModeType.SUBWAY, RouteType.getInValueRange((short)401,(short)402));
      busTypes.forEach( gtfsModeType -> gtfs2PlanitModeMappings.put(gtfsModeType, List.of(PredefinedModeType.BUS)));

      /* lightrail/tram types */
      var tramLightRailTypes = RouteType.getInValueRange((short)900,(short)906);
      modeMappings.put(PredefinedModeType.LIGHTRAIL, tramLightRailTypes);
      tramLightRailTypes.forEach( gtfsModeType -> gtfs2PlanitModeMappings.put(gtfsModeType, List.of(PredefinedModeType.TRAM,PredefinedModeType.LIGHTRAIL)));
    }
    gtfs2PlanitModeMappings.entrySet().forEach(e-> settings.setDefaultGtfs2PredefinedModeTypeMapping(e.getKey(), e.getValue()));

    /* activate all mapped defaults initially*/
    gtfs2PlanitModeMappings.keySet().forEach( gtfsRouteType -> settings.activateGtfsRouteTypeMode(gtfsRouteType));
  }
}
