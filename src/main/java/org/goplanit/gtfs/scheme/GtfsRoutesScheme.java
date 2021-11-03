package org.goplanit.gtfs.scheme;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsObjectType;

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
