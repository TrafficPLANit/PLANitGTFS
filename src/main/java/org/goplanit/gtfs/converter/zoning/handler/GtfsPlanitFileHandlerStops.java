package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.zoning.GtfsPublicTransportReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.gtfs.scheme.GtfsStopsScheme;
import org.goplanit.zoning.Zoning;

/**
 * Handler for handling stops and augmenting a PLANit zoning with the found stops in the process
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerStops extends GtfsFileHandlerStops {

  /**
   * Constructor
   *
   * @param zoningToPopulate the PLANit zoning instance to populate (further)
   * @param settings to apply where needed
   * @param profiler to use
   */
  public GtfsPlanitFileHandlerStops(Zoning zoningToPopulate, GtfsPublicTransportReaderSettings settings, GtfsZoningHandlerProfiler profiler) {
    super();
  }

  /**
   * Handle a GTFS stop
   */
  @Override
  public void handle(GtfsStop gtfsStop) {
    /* to be implemented by derived class, or ignore */
  }

}
