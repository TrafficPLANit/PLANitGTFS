package org.planit.gtfs.handler;

import org.planit.gtfs.model.GtfsShape;
import org.planit.gtfs.scheme.GtfsShapesScheme;

/**
 * Base handler for handling shape attributes
 * 
 * @author markr
 *
 */
public class GtfsFileHandlerShapes extends GtfsFileHandler<GtfsShape> {
  
  /**
   * Constructor
   */
  public GtfsFileHandlerShapes() {
    super(new GtfsShapesScheme());
  }

  /**
   * Handle a GTFS shape rule
   */
  @Override
  public void handle(GtfsShape gtfsShapeRule) {
    /* to be implemented by derived class, or ignore */
  }

}
