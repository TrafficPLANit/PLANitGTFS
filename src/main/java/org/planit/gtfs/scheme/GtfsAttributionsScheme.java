package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS attributions, namely an attributions.txt file and a GtfsAttribution in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsAttributionsScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsAttributionsScheme() {
    super(GtfsFileType.ATTRIBUTIONS, GtfsObjectType.ATTRIBUTION);
  }

}
