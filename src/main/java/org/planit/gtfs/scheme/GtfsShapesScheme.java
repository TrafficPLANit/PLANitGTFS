package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS fare rules, namely a fare_rules.txt file and a GtfsFareRule in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsShapesScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsShapesScheme() {
    super(GtfsFileType.SHAPES, GtfsObjectType.SHAPE);
  }

}
