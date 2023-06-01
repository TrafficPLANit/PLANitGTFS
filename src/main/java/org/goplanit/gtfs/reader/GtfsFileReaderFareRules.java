package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsFareRulesScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS fare rule entries
 * 
 * @author markr
 *
 */
public class GtfsFileReaderFareRules extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderFareRules(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsFareRulesScheme(), gtfsLocation, filePresenceCondition);
  }

}
