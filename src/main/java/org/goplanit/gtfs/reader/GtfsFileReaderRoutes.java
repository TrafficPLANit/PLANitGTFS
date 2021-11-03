package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsRoutesScheme;

/**
 * A GTFS file reader for parsing GTFS routes
 * 
 * @author markr
 *
 */
public class GtfsFileReaderRoutes extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  public GtfsFileReaderRoutes(URL gtfsLocation) {
    super(new GtfsRoutesScheme(), gtfsLocation);
  }

}
