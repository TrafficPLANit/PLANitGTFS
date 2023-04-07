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
   * When a GTFS route has not bene converted into a PLANit route, it is missing for a valid reason. Here, we identify this
   * reason and mark the trip as rmeoved accordingly based on this reason. If the reason cannot be found, an error or wraning is logged
   *
   * @param gtfsTrip for which a Gtfs route does not exist in the PLANit memory model
   */
  private void processMissingRoute(GtfsTrip gtfsTrip) {
    /* a route can be removed based on an earlier check */
    if(!data.isGtfsRouteRemoved(gtfsTrip.getRouteId())) {
      LOGGER.severe(String.format("Unable to find GTFS route %s in PLANit memory model corresponding to GTFS trip %s, GTFS trip ignored", gtfsTrip.getRouteId(), gtfsTrip.getTripId()));
      data.registeredRemovedGtfsTrip(gtfsTrip, GtfsServicesHandlerData.TripRemovalType.UNKNOWN);
      return;
    }

    var removalReason = data.getGtfsRemovedRouteRemovalType(gtfsTrip.getRouteId());
    switch (removalReason) {
      case MODE_INCOMPATIBLE:
        data.registeredRemovedGtfsTrip(gtfsTrip, GtfsServicesHandlerData.TripRemovalType.ROUTE_MODE_INCOMPATIBLE);
        return;
      case SETTINGS_EXCLUDED:
        data.registeredRemovedGtfsTrip(gtfsTrip, GtfsServicesHandlerData.TripRemovalType.ROUTE_EXCLUDED);
        return;
      default:
        LOGGER.severe(String.format("Unable to find GTFS route removal reason for GTFS trip %s, this should not happen, GTFS trip ignored", gtfsTrip.getRouteId(), gtfsTrip.getTripId()));
        data.registeredRemovedGtfsTrip(gtfsTrip, GtfsServicesHandlerData.TripRemovalType.UNKNOWN);
    }
  }

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
    if(!data.isServiceIdActivated(gtfsTrip.getServiceId())){
      /* trip runs on day that is not selected to be parsed at all, discard */
      data.registeredRemovedGtfsTrip(gtfsTrip, GtfsServicesHandlerData.TripRemovalType.SERVICE_ID_DISCARDED);
      return;
    }

    var planitRoutedService = data.getRoutedServiceByExternalId(gtfsTrip.getRouteId());
    if(planitRoutedService == null){
      processMissingRoute(gtfsTrip);
      return;
    }

    // in PLANit we distinguish between scheduled and frequency based trips in their concrete instance. Therefore, we postpone
    // parsing the GTFS entity here until we have identified which of the two this trip relates to (the PLANit trip will be
    // created while parsing stop_times (schedule based trip) and/or frequencies (frequency based trip)
    data.indexByGtfsTripId(gtfsTrip);
  }



}
