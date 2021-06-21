package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsFrequency;
import org.planit.gtfs.scheme.GtfsFrequenciesScheme;

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
