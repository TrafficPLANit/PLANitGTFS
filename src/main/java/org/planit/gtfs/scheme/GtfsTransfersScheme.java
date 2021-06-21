package org.planit.gtfs.scheme;

import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.enums.GtfsObjectType;

/**
 * The scheme used for GTFS transfers, namely a transfers.txt file and a GtfsTransfer in memory object to go with each entry
 * 
 * @author markr
 *
 */
public class GtfsTransfersScheme extends GtfsFileScheme{

  /**
   * Default constructor
   */
  public GtfsTransfersScheme() {
    super(GtfsFileType.TRANSFERS, GtfsObjectType.TRANSFER);
  }

}
