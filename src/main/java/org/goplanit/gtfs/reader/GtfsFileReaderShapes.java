package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsShapesScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS shapes entries
 * 
 * @author markr
 *
 */
public class GtfsFileReaderShapes extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderShapes(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsShapesScheme(), gtfsLocation, filePresenceCondition);
  }

}
