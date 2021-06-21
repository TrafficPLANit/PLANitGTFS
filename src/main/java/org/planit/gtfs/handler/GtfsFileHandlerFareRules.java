package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsFareRule;
import org.planit.gtfs.scheme.GtfsFareRulesScheme;

/**
 * Base handler for handling fare attributes
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerFareRules extends GtfsFileHandler<GtfsFareRule> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerFareRules() {
    super(new GtfsFareRulesScheme());
  }

  /**
   * Handle a GTFS fare rule
   */
  @Override
  public void handle(GtfsFareRule gtfsFareRule) {
    /* to be implemented by derived class, or ignore */
  }

}
