package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsFareAttribute;
import org.planit.gtfs.scheme.GtfsFareAttributesScheme;

/**
 * Base handler for handling fare attributes
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerFareAttributes extends GtfsFileHandler<GtfsFareAttribute> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerFareAttributes() {
    super(new GtfsFareAttributesScheme());
  }

  /**
   * Handle a GTFS fare attribute
   */
  @Override
  public void handle(GtfsFareAttribute gtfsFareAttribute) {
    /* to be implemented by derived class, or ignore */
  }

}
