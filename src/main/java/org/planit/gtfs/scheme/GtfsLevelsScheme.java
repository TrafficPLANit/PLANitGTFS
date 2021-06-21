package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS levels, namely a levels.txt file and a GtfsLevel in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsLevelsScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsLevelsScheme() {
    super(GtfsFileType.LEVELS, GtfsObjectType.LEVEL);
  }

}
