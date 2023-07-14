package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsLevelsScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

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
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderLevels(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsLevelsScheme(), gtfsLocation, filePresenceCondition);
  }

}
