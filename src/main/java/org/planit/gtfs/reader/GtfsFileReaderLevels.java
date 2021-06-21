package org.planit.gtfs.reader;

import java.net.URL;

import org.planit.gtfs.scheme.GtfsLevelsScheme;

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
  public GtfsFileReaderLevels(URL gtfsLocation) {
    super(new GtfsLevelsScheme(), gtfsLocation);
  }

}
