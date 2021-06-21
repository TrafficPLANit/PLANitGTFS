package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS fare rules, namely a fare_rules.txt file and a GtfsFareRule in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsFareRulesScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsFareRulesScheme() {
    super(GtfsFileType.FARE_RULES, GtfsObjectType.FARE_RULE);
  }

}
