package org.goplanit.gtfs.test.utils;

import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;

/**
 * Dedicated class to minimise warnings that have been verified as ignorable and switch them off accordingly
 */
public class SydneyGtfsServicesSettingsUtils {


  /**
   * When applied to the resources based GTFS it suppresses and addresses warnings deemed issues that are NOT to be fixed in the parser
   * but would detract from assessing the logs.
   *
   * @param settings to apply to
   */
  public static void minimiseVerifiedWarnings(GtfsServicesReaderSettings settings) {

    // preliminary --> could not connect GTFS stop pairs, due to waterways connecting stops not in boundingbox
    //                 acitve the below to see which routes frequent those stops
    // Result --> "CCTZ", "CCLC", "F3" --> exclude these routes, so no impossible service leg is created between stops and warning
    //            will go away
    settings.addLogGtfsStopRoutes("2000234", "2000143","2000237");

    /* stop area resides on edge of bounding box, it references entries outside bounding box yielding (valid but uncorrectable) warnings */
    settings.excludeGtfsRouteByShortName(
        "CCTZ",
        "CCLC",
        "F3"
    );


  }
}
