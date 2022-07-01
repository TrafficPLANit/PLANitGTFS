package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsTranslation;
import org.goplanit.gtfs.scheme.GtfsTranslationsScheme;

/**
 * Base handler for handling translations
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerTranslations extends GtfsFileHandler<GtfsTranslation> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerTranslations() {
    super(new GtfsTranslationsScheme());
  }

  /**
   * Handle a GTFS translation
   */
  @Override
  public void handle(GtfsTranslation gtfsTranslation) {
    /* to be implemented by derived class, or ignore */
  }

}
