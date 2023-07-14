package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsTranslationsScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS translation entries
 * 
 * @author markr
 *
 */
public class GtfsFileReaderTranslations extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderTranslations(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsTranslationsScheme(), gtfsLocation, filePresenceCondition);
  }

}
