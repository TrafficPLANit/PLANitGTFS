package org.planit.gtfs.reader;

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
   * @return created reader
   */
  public static GtfsReader createDefaultReader() {
    return new GtfsReader();
  }

  /** Factory method to create a GTFS file specific reader
   * 
   * @param fileScheme to create reader for
   * @return created file reader
   */
  public static GtfsFileReaderBase createFileReader(GtfsFileScheme fileScheme) {
    switch (fileScheme.getFileType()) {
      case TRIPS:
        return new GtfsFileReaderTrips();
      default:
        LOGGER.warning(String.format("Unable to create GTFS file reader for given scheme %s", fileScheme.toString()));
        return null;
    }
  }
   
  

}
