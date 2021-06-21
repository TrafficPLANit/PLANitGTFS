package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS stops, namely an stops.txt file and a GtfsStop in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsStopsScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsStopsScheme() {
    super(GtfsFileType.STOPS, GtfsObjectType.STOP);
  }

}
