package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.entity.GtfsCalendar;
import org.goplanit.gtfs.entity.GtfsTrip;
import org.goplanit.gtfs.handler.GtfsFileHandlerCalendars;
import org.goplanit.gtfs.handler.GtfsFileHandlerTrips;
import org.goplanit.utils.exceptions.PlanItRunTimeException;

import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Handler for handling calendar entries so we can filter a PLANit (Service) network and trips with the found GTFS trips for the appropriate day/time period.
 * <p>
 *   Prerequisite: no prerequisites
 * </p>
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerCalendar extends GtfsFileHandlerCalendars {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerCalendar.class.getCanonicalName());

  /** track internal data used to efficiently handle the parsing */
  private final GtfsServicesHandlerData data;

  /** test on each row, when true keep service id, otherwise discard */
  private final Predicate<GtfsCalendar> serviceIdFilter;

  /**
   * Constructor
   *
   * @param gtfsServicesHandlerData      containing all data to track and resources needed to perform the processing
   * @param serviceIdFilter             filters each row whether to register the service id (when true), or not (when false)
   */
  public GtfsPlanitFileHandlerCalendar(final GtfsServicesHandlerData gtfsServicesHandlerData, Predicate<GtfsCalendar> serviceIdFilter) {
    super();
    this.data = gtfsServicesHandlerData;
    this.serviceIdFilter = serviceIdFilter;
  }

  /**
   * Handle a GTFS calendar row
   */
  @Override
  public void handle(GtfsCalendar gtfsCalendar) {

    if(serviceIdFilter.test(gtfsCalendar)){
      data.registerActiveServiceId(gtfsCalendar.getServiceId());
    }

  }

}
