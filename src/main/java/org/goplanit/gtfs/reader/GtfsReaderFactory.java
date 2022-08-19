package org.goplanit.gtfs.reader;

import java.net.URL;
import java.util.logging.Logger;

import org.goplanit.gtfs.enums.GtfsColumnType;
import org.goplanit.gtfs.scheme.GtfsFileScheme;
import org.goplanit.utils.misc.UrlUtils;

/**
 * top level class to get things started. Based on location (dir or zip file) and scheme (type of GTFS file), create a single stand-alone
 * GTFS file reader/handler combination, or an umbrella GTFS reader that is capable of registering multiple file readers/handlers
 * 
 * @author markr
 *
 */
public class GtfsReaderFactory {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsReaderFactory.class.getCanonicalName());

  /** Factory method to create a GTFS reader supporting one or more file readers where  all columns will be parsed by default
   *
   * @param gtfsLocation to use to extract GTFS file(s) from
   * @return created reader
   */
  public static GtfsReader createDefaultReader(URL gtfsLocation) {
    return new GtfsReader(gtfsLocation, GtfsColumnType.ALL_COLUMNS);
  }

  /**
   * Identical to {@link #createFileReader(GtfsFileScheme, URL, GtfsColumnType)} only allowing for string based gtfs location reflecting
   * a path to gtfs file and setting initially all columns to be parsed
   *
   * @param fileScheme to apply
   * @param gtfsLocation to use
   * @return created file reader based on scheme
   */
  public static GtfsFileReaderBase createFileReader(GtfsFileScheme fileScheme, String gtfsLocation) {
    return createFileReader(fileScheme,  UrlUtils.createFromPath(gtfsLocation));
  }

  /**
   * Identical to {@link #createFileReader(GtfsFileScheme, URL, GtfsColumnType)} only allowing for string based gtfs location reflecting
   * a path to gtfs file
   *
   * @param fileScheme to apply
   * @param gtfsLocation to use
   * @param columnType the way we configure the initial included columns across all GTFS files
   * @return created file reader based on scheme
   */
  public static GtfsFileReaderBase createFileReader(GtfsFileScheme fileScheme, String gtfsLocation, GtfsColumnType columnType) {
    return createFileReader(fileScheme, UrlUtils.createFromPath(gtfsLocation), columnType);
  }

  /** Factory method to create a GTFS file specific reader with all columns initially included
   *
   * @param fileScheme to create reader for
   * @param gtfsLocation to use to extract GTFS file from
   * @return created file reader
   */
  public static GtfsFileReaderBase createFileReader(GtfsFileScheme fileScheme, URL gtfsLocation) {
    return createFileReader(fileScheme, gtfsLocation, GtfsColumnType.ALL_COLUMNS);
  }

  /** Factory method to create a GTFS file specific reader
   * 
   * @param fileScheme to create reader for
   * @param gtfsLocation to use to extract GTFS file from
   * @param columnType the way we configure the initial included columns across all GTFS files
   * @return created file reader
   */
  public static GtfsFileReaderBase createFileReader(GtfsFileScheme fileScheme, URL gtfsLocation, GtfsColumnType columnType) {
    GtfsFileReaderBase createdReader = null;
    switch (fileScheme.getFileType()) {
      case AGENCIES:
        createdReader = new GtfsFileReaderAgencies(gtfsLocation);
        break;
      case ATTRIBUTIONS:
        createdReader = new GtfsFileReaderAttributions(gtfsLocation);
        break;
      case CALENDARS:
        createdReader = new GtfsFileReaderCalendars(gtfsLocation);
        break;
      case CALENDAR_DATES:
        createdReader = new GtfsFileReaderCalendarDates(gtfsLocation);
        break;
      case FARE_ATTRIBUTES:
        createdReader = new GtfsFileReaderFareAttributes(gtfsLocation);
        break;
      case FARE_RULES:
        createdReader = new GtfsFileReaderFareRules(gtfsLocation);
        break;
      case FEED_INFO:
        createdReader = new GtfsFileReaderFeedInfo(gtfsLocation);
        break;
      case FREQUENCIES:
        createdReader = new GtfsFileReaderFrequencies(gtfsLocation);
        break;
      case LEVELS:
        createdReader = new GtfsFileReaderLevels(gtfsLocation);
        break;
      case PATHWAYS:
        createdReader = new GtfsFileReaderPathways(gtfsLocation);
        break;
      case ROUTES:
        createdReader = new GtfsFileReaderRoutes(gtfsLocation);
        break;
      case SHAPES:
        createdReader = new GtfsFileReaderShapes(gtfsLocation);
        break;
      case TRANSFERS:
        createdReader = new GtfsFileReaderTransfers(gtfsLocation);
        break;
      case TRANSLATIONS:
        createdReader = new GtfsFileReaderTranslations(gtfsLocation);
        break;
      case TRIPS:
        createdReader = new GtfsFileReaderTrips(gtfsLocation);
        break;
      case STOP_TIMES:
        createdReader = new GtfsFileReaderStopTimes(gtfsLocation);
        break;
      case STOPS:
        createdReader = new GtfsFileReaderStops(gtfsLocation);
        break;
      default:
        LOGGER.warning(String.format("Unable to create GTFS file reader for given scheme %s", fileScheme.toString()));
        return null;
    }

    createdReader.initialiseColumnConfiguration(columnType);
    return createdReader;
  }
   
  

}
