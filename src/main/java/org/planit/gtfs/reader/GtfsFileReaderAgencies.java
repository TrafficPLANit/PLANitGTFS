package org.planit.gtfs.reader;

import java.net.URL;

import org.planit.gtfs.scheme.GtfsAgenciesScheme;

/**
 * A GTFS file reader for parsing GTFS agencies
 * 
 * @author markr
 *
 */
public class GtfsFileReaderAgencies extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  public GtfsFileReaderAgencies(URL gtfsLocation) {
    super(new GtfsAgenciesScheme(), gtfsLocation);
  }

}
