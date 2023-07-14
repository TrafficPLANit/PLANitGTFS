package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsTransfer;
import org.goplanit.gtfs.scheme.GtfsTransfersScheme;

/**
 * Base handler for handling transfers
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerTransfers extends GtfsFileHandler<GtfsTransfer> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerTransfers() {
    super(new GtfsTransfersScheme());
  }

  /**
   * Handle a GTFS transfer
   */
  @Override
  public void handle(GtfsTransfer gtfsTransfer) {
    /* to be implemented by derived class, or ignore */
  }

}
