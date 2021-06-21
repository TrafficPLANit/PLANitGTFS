package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsTranslation;
import org.planit.gtfs.scheme.GtfsTranslationsScheme;

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
