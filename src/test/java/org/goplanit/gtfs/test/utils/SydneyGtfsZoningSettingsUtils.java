package org.goplanit.gtfs.test.utils;

import org.goplanit.converter.idmapping.IdMapperType;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;

/**
 * Dedicated class to minimise warnings that have been verified as ignorable and switch them off accordingly
 */
public class SydneyGtfsZoningSettingsUtils {


  /**
   * When applied to the resource GTFS files it suppresses and addresses warnings deemed issues that are NOT to be fixed in the parser
   * but would detract from assessing the logs.
   *
   * @param settings to apply to
   */
  public static void minimiseVerifiedWarnings(GtfsZoningReaderSettings settings) {


    /* stop area resides on edge of bounding box, it references entries outside bounding box yielding (valid but uncorrectable) warnings */
    settings.excludeGtfsStopsById(
        "201142",   /* near bounding box, no roads available in PLANit */
        "2000252",              /* near bounding box, no roads available in PLANit */
        "2000307",              /* near bounding box, no roads available in PLANit */
        "600318850",            /* near bounding box, no roads available in PLANit */
        "2000282",              /* near bounding box, no roads available in PLANit */
        "2000426",              /* near bounding box, no roads available in PLANit */
        "206120",                /* near bounding box, no eligible road available in PLANit */

        "206125",                /* near bounding box, correct road not available in PLANit, matched to wrong road */
        "2000249",               /* near bounding box, correct road not available in PLANit, matched to wrong road */
        "2000140",               /* near bounding box, correct road not available in PLANit, matched to wrong road */

        "200043",                /* eligible road available but due to cutting off network, no route possible to next stop, exclude */
        "200044"                /* eligible road available but due to cutting off network, no route possible to next stop, exclude */
    );

    /* location not present as OSM stop and too close to a major overpass it does not reside on but otherwise would get mapped to,
     * force mapping to PLANit link based on its external (OSM) id
     */
    settings.overwriteGtfsStopToLinkMapping("200018","150290323", IdMapperType.EXTERNAL_ID);

    /** location of GTFS stop too far from OSM/PLANit stop, but it is the same stop so should be mapped to it, explicitly map */
    settings.setOverwriteGtfsStopTransferZoneMapping("2000283", "3814715779", IdMapperType.EXTERNAL_ID);

    /* two very closeby GTFS stops are mapped to the same transfer zone, but this is not ideal, based on logging, we therefore
     * indicate this should not be allowed and avoid this warning
     */
    settings.disallowGtfsStopToTransferZoneJointMapping("200081");

    /* when a GTFS stop is mapped to the wrong OSM stop (or produces a warning it could not be matched to any nearby existing stop
     * and it is verified this is correct behaviour) but it is situated in the correct location and should result in a new transfer zone,
     * we want to force the GTFS stop to be created separately (without warning), this option allows for this.
     * It is typically populated based on logging feedback indicating a potential problem.
     */
    settings.forceCreateNewTransferZoneForGtfsStops(
        "2000232",  /* would otherwise be matched to other stand nearby which is incorrect, force create missing (OSM) stand */
        "2000418",
        "206036",    /* Bus stop near ferry terminal, generates warning it is not matched (as it should), but this eliminates the warning */
        "200031",    /* Bus Stand not present in OSM/PLANit, force create so we do not get warnings of not matching to nearby other stands */
        "2000226",   /* Bus stop missing in OSM/PLANit as of yet, force create so we do not get warnings of not matching to stop on opposite side of street */
        "2000140"    /* Bus stop missing in OSM/PLANit as of yet, force create so we do not get warnings of not matching to other closeby stops  */
    );

  }
}
