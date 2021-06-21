package org.planit.gtfs.reader;

import java.net.URL;
import java.util.logging.Logger;

import org.planit.gtfs.scheme.GtfsFileScheme;

/**
 * top level class to get things started. BAsed on location (dir or zip file) and scheme (type of GTFS file), create a single stand-alone 
 * GTFS file reader/handler combination, or an umbrella GTFS reader that is capable of registering multiple file readers/handlers
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
      case AGENCIES:
        return new GtfsFileReaderAgencies(gtfsLocation);
      case CALENDARS:
        return new GtfsFileReaderCalendars(gtfsLocation);        
      case ROUTES:
        return new GtfsFileReaderRoutes(gtfsLocation);        
      case TRIPS:
        return new GtfsFileReaderTrips(gtfsLocation);
      case STOP_TIMES:
        return new GtfsFileReaderStopTimes(gtfsLocation);        
      case STOPS:
        return new GtfsFileReaderStops(gtfsLocation);        
      default:
        LOGGER.warning(String.format("Unable to create GTFS file reader for given scheme %s", fileScheme.toString()));
        return null;
    }
  }
   
  

}
