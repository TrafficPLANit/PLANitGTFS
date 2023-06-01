package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsPathwaysScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS pathways
 * 
 * @author markr
 *
 */
public class GtfsFileReaderPathways extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderPathways(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsPathwaysScheme(), gtfsLocation, filePresenceCondition);
  }

}
