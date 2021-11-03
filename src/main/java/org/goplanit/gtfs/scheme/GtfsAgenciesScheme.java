package org.goplanit.gtfs.scheme;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS agencies, namely an agency.txt file and a GtfsAgency in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsAgenciesScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsAgenciesScheme() {
    super(GtfsFileType.AGENCIES, GtfsObjectType.AGENCY);
  }

}
