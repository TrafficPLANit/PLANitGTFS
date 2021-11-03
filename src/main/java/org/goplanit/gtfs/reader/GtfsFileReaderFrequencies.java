package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsFrequenciesScheme;

/**
 * A GTFS file reader for parsing GTFS frequencies
 * 
 * @author markr
 *
 */
public class GtfsFileReaderFrequencies extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  public GtfsFileReaderFrequencies(URL gtfsLocation) {
    super(new GtfsFrequenciesScheme(), gtfsLocation);
  }

}
