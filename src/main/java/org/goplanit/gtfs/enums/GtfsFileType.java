package org.goplanit.gtfs.enums;

/**
 * Supported GTFS file types and their corresponding (default) file name in which entries are expected to be found
 * 
 * @author markr
 *
 */
public enum GtfsFileType {
    
  AGENCIES("agency.txt"),
  ATTRIBUTIONS("attributions.txt"),
  CALENDARS("calendar.txt"),
  CALENDAR_DATES("calendar_dates.txt"),
  FARE_ATTRIBUTES("fare_attributes.txt"),
  FARE_RULES("fare_rules.txt"),
  FEED_INFO("feed_info.txt"),
  FREQUENCIES("frequencies.txt"),
  LEVELS("levels.txt"),
  PATHWAYS("pathways.txt"),
  ROUTES("routes.txt"),
  STOP_TIMES("stop_times.txt"),  
  STOPS("stops.txt"), 
  TRANSFERS("transfers.txt"),
  TRANSLATIONS("translations.txt"),
  TRIPS("trips.txt"), 
  SHAPES("shapes.txt");       

  private final String value;
  
  /** Create a GTFS file type
   * 
   * @param value to use
   */
  private GtfsFileType(String value){
    this.value = value;
  }
  
  /** Get the value of the enum
   * 
   * @return value of the enum
   */
  public String value() {
    return value;
  }
  
}
