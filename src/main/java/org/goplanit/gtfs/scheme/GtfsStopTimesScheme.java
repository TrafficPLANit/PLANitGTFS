package org.goplanit.gtfs.scheme;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS stop times, namely a stop_times.txt file and a GtfsStopTime in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsStopTimesScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsStopTimesScheme() {
    super(GtfsFileType.STOP_TIMES, GtfsObjectType.STOP_TIME);
  }

}
