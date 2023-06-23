package org.goplanit.gtfs.test.utils;

import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;

/**
 * Dedicated class to minimise warnings that have been verified as ignorable and switch them off accordingly
 */
public class MelbourneGtfsServicesSettingsUtils {


  /**
   * When applied to the 2023 PBF it suppresses and addresses warnings deemed issues that are NOT to be fixed in the parser
   * but would detract from assessing the logs.
   *
   * @param settings to apply to
   */
  public static void minimiseVerifiedWarnings2023(GtfsServicesReaderSettings settings) {

  }
}
