package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS pathways, namely a pathways.txt file and a GtfsPathway in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsPathwaysScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsPathwaysScheme() {
    super(GtfsFileType.PATHWAYS, GtfsObjectType.PATHWAY);
  }

}
