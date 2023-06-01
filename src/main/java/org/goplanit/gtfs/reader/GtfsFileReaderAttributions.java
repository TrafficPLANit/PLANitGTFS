package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsAttributionsScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS attributions entries
 * 
 * @author markr
 *
 */
public class GtfsFileReaderAttributions extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderAttributions(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsAttributionsScheme(), gtfsLocation, filePresenceCondition);
  }

}
