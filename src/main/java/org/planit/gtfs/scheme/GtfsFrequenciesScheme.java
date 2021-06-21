package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS frequencies, namely a frequencies.txt file and a GtfsFrequency in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsFrequenciesScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsFrequenciesScheme() {
    super(GtfsFileType.FREQUENCIES, GtfsObjectType.FREQUENCY);
  }

}
