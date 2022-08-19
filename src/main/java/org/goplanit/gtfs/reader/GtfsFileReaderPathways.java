package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsPathwaysScheme;

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
   */
  protected GtfsFileReaderPathways(URL gtfsLocation) {
    super(new GtfsPathwaysScheme(), gtfsLocation);
  }

}
