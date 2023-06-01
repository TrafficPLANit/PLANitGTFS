package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsFareAttributesScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

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
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderFareAttributes(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsFareAttributesScheme(), gtfsLocation, filePresenceCondition);
  }

}
