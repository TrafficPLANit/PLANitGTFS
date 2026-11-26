package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;

/**
 * Handler finding which GTFS stops lie within the area the run covers, so the services stage can tell a trip that was
 * never within the area from one the parser lost.
 * <p>
 * Kept apart from the zoning stage's stop handler, which maps stops onto the physical network, names them and reports
 * on them. Here nothing is mapped and nothing is recorded against the stop: only the spatial question is answered, and
 * the answer is held only for the stops within the area, as they are by far the fewer.
 * </p>
 * <p>
 *   Prerequisite: no prerequisites
 * </p>
 *
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerStopScope extends GtfsFileHandlerStops {

  /** track internal data used to efficiently handle the parsing */
  private final GtfsServicesHandlerData data;

  /**
   * Constructor
   *
   * @param gtfsServicesHandlerData      containing all data to track and resources needed to perform the processing
   */
  public GtfsPlanitFileHandlerStopScope(final GtfsServicesHandlerData gtfsServicesHandlerData) {
    super();
    this.data = gtfsServicesHandlerData;
  }

  /**
   * Handle a GTFS stop
   */
  @Override
  public void handle(GtfsStop gtfsStop) {

    var stopLocation = gtfsStop.getLocationAsPoint();
    if(stopLocation == null){
      /* without a location there is nothing to place, so it cannot be ruled beyond the area */
      data.registerGtfsStopWithinArea(gtfsStop.getStopId());
      return;
    }

    if(data.getBoundingAreaHelper().isPartlyOrWhollyWithinBoundaryArea(stopLocation, true)){
      data.registerGtfsStopWithinArea(gtfsStop.getStopId());
    }
  }

}
