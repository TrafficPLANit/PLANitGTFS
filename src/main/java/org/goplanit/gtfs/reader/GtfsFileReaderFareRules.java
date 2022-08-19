package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsFareRulesScheme;

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
   */
  protected GtfsFileReaderFareRules(URL gtfsLocation) {
    super(new GtfsFareRulesScheme(), gtfsLocation);
  }

}
