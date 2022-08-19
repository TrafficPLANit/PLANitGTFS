package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsLevelsScheme;

/**
 * A GTFS file reader for parsing GTFS levels
 * 
 * @author markr
 *
 */
public class GtfsFileReaderLevels extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  protected GtfsFileReaderLevels(URL gtfsLocation) {
    super(new GtfsLevelsScheme(), gtfsLocation);
  }

}
