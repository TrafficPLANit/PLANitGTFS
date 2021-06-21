package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS routes, namely a routes.txt file and a GtfsAgency in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsRoutesScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsRoutesScheme() {
    super(GtfsFileType.ROUTES, GtfsObjectType.ROUTE);
  }

}
