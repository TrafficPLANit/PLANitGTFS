package org.goplanit.gtfs.test.utils;

import org.goplanit.converter.idmapping.IdMapperType;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;

/**
 * Dedicated class to minimise warnings that have been verified as ignorable and switch them off accordingly
 */
public class MelbourneGtfsZoningSettingsUtils {


  /**
   * When applied to the GTFs resources it suppresses and addresses warnings deemed issues that are NOT to be fixed in the parser
   * but would detract from assessing the logs.
   *
   * @param settings to apply to
   * @param preExistingTransferZonesPresent when true there already exist transfer zones to map to, when false not
   */
  public static void minimiseVerifiedWarnings2023(GtfsZoningReaderSettings settings , boolean preExistingTransferZonesPresent) {

    if(preExistingTransferZonesPresent) {
      /* PLANit correctly inferred existing transfer zone mapping but triggered warning due to potential (link) proximity issue -
       * suppress warning by explicitly mapping to suggest transfer zone */
      settings.setOverwriteGtfsStopTransferZoneMapping("10251", "8019982002", IdMapperType.EXTERNAL_ID);
    }

    /* on wrong side of road, and/or too close to another road and not OSM stop to match to, causing the wrong mapping
     * overwrite location to more appropriate point to avoid this mismatch */
    settings.setOverwriteGtfsStopLocation("1122",-37.83108,144.98808);

    /* same reason as above, but here, we map to non-chosen closest link explicitly as it is the correct link */
    settings.overwriteGtfsStopToLinkMapping("1128","977507781", IdMapperType.EXTERNAL_ID);

  }
}