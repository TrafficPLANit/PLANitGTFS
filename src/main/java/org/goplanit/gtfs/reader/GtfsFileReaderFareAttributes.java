package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsFareAttributesScheme;

/**
 * A GTFS file reader for parsing GTFS fare attributes entries
 * 
 * @author markr
 *
 */
public class GtfsFileReaderFareAttributes extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  public GtfsFileReaderFareAttributes(URL gtfsLocation) {
    super(new GtfsFareAttributesScheme(), gtfsLocation);
  }

}
