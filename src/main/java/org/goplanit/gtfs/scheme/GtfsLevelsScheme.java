package org.goplanit.gtfs.scheme;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsObjectType;

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
