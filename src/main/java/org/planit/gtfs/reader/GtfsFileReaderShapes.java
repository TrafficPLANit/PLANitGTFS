package org.planit.gtfs.reader;

import java.net.URL;

import org.planit.gtfs.scheme.GtfsShapesScheme;

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
   */
  public GtfsFileReaderShapes(URL gtfsLocation) {
    super(new GtfsShapesScheme(), gtfsLocation);
  }

}
