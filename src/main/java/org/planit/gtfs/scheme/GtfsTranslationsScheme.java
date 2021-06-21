package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS translations, namely a translations.txt file and a GtfsTranslation in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsTranslationsScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsTranslationsScheme() {
    super(GtfsFileType.TRANSLATIONS, GtfsObjectType.TRANSLATION);
  }

}
