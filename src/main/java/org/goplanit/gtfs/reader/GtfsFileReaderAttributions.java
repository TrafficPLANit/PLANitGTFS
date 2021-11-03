package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsAttributionsScheme;

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
   */
  public GtfsFileReaderAttributions(URL gtfsLocation) {
    super(new GtfsAttributionsScheme(), gtfsLocation);
  }

}
