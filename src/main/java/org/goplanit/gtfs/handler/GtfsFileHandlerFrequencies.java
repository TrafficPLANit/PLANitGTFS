package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsFrequency;
import org.goplanit.gtfs.scheme.GtfsFrequenciesScheme;

/**
 * Base handler for handling frequencies
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerFrequencies extends GtfsFileHandler<GtfsFrequency> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerFrequencies() {
    super(new GtfsFrequenciesScheme());
  }

  /**
   * Handle a GTFS frequency
   */
  @Override
  public void handle(GtfsFrequency gtfsFrequency) {
    /* to be implemented by derived class, or ignore */
  }

}
