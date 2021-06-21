package org.planit.gtfs.reader;

import java.net.URL;

import org.planit.gtfs.scheme.GtfsTranslationsScheme;

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
   */
  public GtfsFileReaderTranslations(URL gtfsLocation) {
    super(new GtfsTranslationsScheme(), gtfsLocation);
  }

}
