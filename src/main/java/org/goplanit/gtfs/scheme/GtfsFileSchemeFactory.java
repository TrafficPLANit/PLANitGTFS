package org.goplanit.gtfs.scheme;

import java.util.logging.Logger;

import org.goplanit.gtfs.enums.GtfsFileType;

/**
 * Factory class to create most common GTFS file schemes
 * 
 * @author markr
 *
 */
public class GtfsFileSchemeFactory {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsFileSchemeFactory.class.getCanonicalName());

  /** Create the Gtfs file scheme based on the desired file type
   * 
   * @param fileType to base scheme on
   * @return created scheme
   */
  public static GtfsFileScheme create(GtfsFileType fileType) {
    switch (fileType) {
      case AGENCIES:              
        return new GtfsAgenciesScheme();
      case ATTRIBUTIONS:              
        return new GtfsAttributionsScheme();
      case CALENDAR_DATES:              
        return new GtfsCalendarDatesScheme();
      case CALENDARS:              
        return new GtfsCalendarsScheme();
      case FARE_ATTRIBUTES:              
        return new GtfsFareAttributesScheme();
      case FARE_RULES:              
        return new GtfsFareRulesScheme();
      case FEED_INFO:              
        return new GtfsFeedInfoScheme();
      case FREQUENCIES:              
        return new GtfsFrequenciesScheme();
      case LEVELS:              
        return new GtfsLevelsScheme();
      case PATHWAYS:              
        return new GtfsPathwaysScheme();
      case ROUTES:              
        return new GtfsRoutesScheme();
      case SHAPES:              
        return new GtfsShapesScheme();
      case STOP_TIMES:              
        return new GtfsStopTimesScheme();
      case STOPS:              
        return new GtfsStopsScheme();
      case TRANSFERS:              
        return new GtfsTransfersScheme();
      case TRANSLATIONS:              
        return new GtfsTranslationsScheme();
      case TRIPS:              
        return new GtfsTripsScheme();        
      default:
        LOGGER.warning(String.format("Unsupported GTFS file type %s", fileType));
        return null;
    }
  }
}
