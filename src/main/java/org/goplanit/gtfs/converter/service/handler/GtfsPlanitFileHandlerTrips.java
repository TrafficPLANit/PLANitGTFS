package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.entity.GtfsTrip;
import org.goplanit.gtfs.handler.GtfsFileHandlerTrips;
import org.goplanit.utils.exceptions.PlanItRunTimeException;

import java.util.logging.Logger;

/**
 * Handler for handling trips and populating a PLANit (Service) network and trips with the found GTFS trips.
 * <p>
 *   Prerequisite: It is assumed GTFS routes and calendar have been parsed already and PLANit entities are available to collect by GTFS route id
 * </p>
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerTrips extends GtfsFileHandlerTrips {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerTrips.class.getCanonicalName());

  /** track internal data used to efficiently handle the parsing */
  private final GtfsServicesHandlerData data;

  /**
   * Constructor
   *
   * @param gtfsServicesHandlerData      containing all data to track and resources needed to perform the processing
   */
  public GtfsPlanitFileHandlerTrips(final GtfsServicesHandlerData gtfsServicesHandlerData) {
    super();
    this.data = gtfsServicesHandlerData;

    PlanItRunTimeException.throwIfNull(data.getRoutedServices(), "Routed services not present, unable to parse GTFS trips");
    PlanItRunTimeException.throwIfNull(data.getServiceNetwork(), "Services network not present, unable to parse GTFS trips");
    PlanItRunTimeException.throwIfNull(data.hasActiveServiceIds(), "GTFS Calendar likely not yet parsed, no service ids activated, unable to parse GTFS trips");
    // prerequisites
    PlanItRunTimeException.throwIf(data.getRoutedServices().getLayers().isEachLayerEmpty()==true,"No GTFS routes parsed yet, unable to parse GTFS trips");
  }

  /**
   * Handle a GTFS trip
   */
  @Override
  public void handle(GtfsTrip gtfsTrip) {

    var planitRoutedService = data.getRoutedServiceByExternalId(gtfsTrip.getRouteId());
    if(planitRoutedService == null){
      if(data.isGtfsRouteDiscarded(gtfsTrip.getRouteId())){
        data.registeredDiscardedTrip(gtfsTrip, GtfsServicesHandlerData.TripDiscardType.ROUTE_DISCARDED);
        return;
      }
      LOGGER.severe(String.format("Unable to find GTFS route %s in PLANit memory model corresponding to GTFS trip %s, GTFS trip ignored", gtfsTrip.getRouteId(), gtfsTrip.getTripId()));
      return;
    }

    if(!data.isActiveServiceId(gtfsTrip.getServiceId())){
      /* trip runs on day that is not selected to be parsed, discard */
      data.registeredDiscardedTrip(gtfsTrip, GtfsServicesHandlerData.TripDiscardType.SERVICE_ID_DISCARDED);
      return;
    }

    // in PLANit we distinguish between scheduled and frequency based trips in their concrete instance. Therefore, we postpone
    // parsing the GTFS entity here until we have identified which of the two this trip relates to (the PLANit trip will be
    // created while parsing stop_times (schedule based trip) and/or frequencies (frequency based trip)
    data.indexByGtfsTripId(gtfsTrip);
  }

}
