package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS fare attributes, namely a fare_attributes.txt file and a GtfsFareAttribute in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsFareAttributesScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsFareAttributesScheme() {
    super(GtfsFileType.FARE_ATTRIBUTES, GtfsObjectType.FARE_ATTRIBUTE);
  }

}
