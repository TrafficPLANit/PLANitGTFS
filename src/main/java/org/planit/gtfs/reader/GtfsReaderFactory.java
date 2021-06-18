package org.planit.gtfs.reader;

import java.net.URL;
import java.util.logging.Logger;

import org.planit.gtfs.scheme.GtfsFileScheme;

/**
 * top lvele class to get things started
 * 
 * @author markr
 *
 */
public class GtfsReaderFactory {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsReaderFactory.class.getCanonicalName());

  /** Factory method to create a GTFS reader supporting one or more file readers
   *
   * @param gtfsLocation to use to extract GTFS file(s) from
   * @return created reader
   */
  public static GtfsReader createDefaultReader(URL gtfsLocation) {
    return new GtfsReader(gtfsLocation);
  }

  /** Factory method to create a GTFS file specific reader
   * 
   * @param fileScheme to create reader for
   * @param gtfsLocation to use to extract GTFS file from
   * @return created file reader
   */
  public static GtfsFileReaderBase createFileReader(GtfsFileScheme fileScheme, URL gtfsLocation) {
    switch (fileScheme.getFileType()) {
      case TRIPS:
        return new GtfsFileReaderTrips(gtfsLocation);
      default:
        LOGGER.warning(String.format("Unable to create GTFS file reader for given scheme %s", fileScheme.toString()));
        return null;
    }
  }
   
  

}
