package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.converter.diagnostics.GtfsEntityScope;
import org.goplanit.gtfs.converter.diagnostics.GtfsScopeDimension;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseIssue;
import org.goplanit.gtfs.entity.GtfsTrip;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandlerTrips;
import org.goplanit.utils.exceptions.PlanItRunTimeException;

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

  /** track internal data used to efficiently handle the parsing */
  private final GtfsServicesHandlerData data;

  /**
   * When a GTFS route has not been converted into a PLANit route, it is missing for a valid reason. Here, we identify this
   * reason and mark the trip as removed accordingly based on this reason. If the reason cannot be found, an error or wraning is logged
   *
   * @param gtfsTrip for which a Gtfs route does not exist in the PLANit memory model
   */
  private void processMissingRoute(GtfsTrip gtfsTrip) {
    /* a route is absent because it was discarded earlier, in which case its cause determines the trip's */
    var routeIssue = data.getDiagnostics().getDiscardIssue(GtfsObjectType.ROUTE, gtfsTrip.getRouteId());
    var tripIssue = GtfsParseIssue.TRIP_ROUTE_MISSING_UNEXPLAINED;
    if(routeIssue != null) {
      switch (routeIssue) {
        case ROUTE_MODE_NOT_ACTIVATED:
          /* the route was dropped for its mode, not for being left out by name, so the trip passed selection and
           * stands outside only the modes the run covers */
          data.getDiagnostics().registerSeenWithinScope(
              GtfsObjectType.TRIP, GtfsScopeDimension.SELECTION, gtfsTrip.getTripId());
          data.getDiagnostics().registerSeenOutOfScope(
              GtfsObjectType.TRIP, GtfsScopeDimension.MODAL, gtfsTrip.getTripId());
          tripIssue = GtfsParseIssue.TRIP_ROUTE_MODE_NOT_ACTIVATED;
          break;
        case ROUTE_EXCLUDED_BY_SETTINGS:
          /* the route was left out by name, which leaves the trip out of the run's business with it */
          data.getDiagnostics().registerSeenOutOfScope(
              GtfsObjectType.TRIP, GtfsScopeDimension.SELECTION, gtfsTrip.getTripId());
          tripIssue = GtfsParseIssue.TRIP_ROUTE_DISCARDED;
          break;
        case ROUTE_NO_SERVICES_LAYER_FOR_MODE:
          tripIssue = GtfsParseIssue.TRIP_ROUTE_WITHOUT_SERVICES_LAYER;
          break;
        default:
          /* the route was discarded for a cause carrying no trip equivalent, which leaves the trip unexplained */
          break;
      }
    }

    data.getDiagnostics().registerIssue(tripIssue, gtfsTrip.getTripId(), gtfsTrip.getRouteId());
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
    data.getProfiler().registerSeenTrip();

    /* TEMPORAL SCOPE: the day the trip runs is known here, and its route stands in time wherever any of its trips
     * do, so a route is only beyond the chosen day once every one of its trips is */
    var diagnostics = data.getDiagnostics();
    boolean activeOnChosenDay = data.isServiceIdActivated(gtfsTrip.getServiceId());
    if(activeOnChosenDay){
      diagnostics.registerSeenWithinScope(
          GtfsObjectType.TRIP, GtfsScopeDimension.TEMPORAL, gtfsTrip.getTripId());
    }else{
      diagnostics.registerSeenOutOfScope(
          GtfsObjectType.TRIP, GtfsScopeDimension.TEMPORAL, gtfsTrip.getTripId());
    }
    diagnostics.registerSeenPartInScope(
        GtfsObjectType.ROUTE, GtfsScopeDimension.TEMPORAL, gtfsTrip.getRouteId(),
        activeOnChosenDay ? GtfsEntityScope.IN : GtfsEntityScope.OUT);

    if(!activeOnChosenDay){
      /* trip runs on day that is not selected to be parsed at all, discard */
      diagnostics.registerIssue(GtfsParseIssue.TRIP_SERVICE_ID_NOT_ACTIVE_ON_DAY, gtfsTrip.getTripId());
      return;
    }

    var planitRoutedService = data.getRoutedServiceByExternalId(gtfsTrip.getRouteId());
    if(planitRoutedService == null){
      processMissingRoute(gtfsTrip);
      return;
    }

    /* SELECTION and MODAL SCOPE: the route survived being left out by name and its own mode test, so the trip
     * belonging to it does too */
    diagnostics.registerSeenWithinScope(
        GtfsObjectType.TRIP, GtfsScopeDimension.SELECTION, gtfsTrip.getTripId());
    diagnostics.registerSeenWithinScope(
        GtfsObjectType.TRIP, GtfsScopeDimension.MODAL, gtfsTrip.getTripId());

    // in PLANit we distinguish between scheduled and frequency based trips in their concrete instance. Therefore, we postpone
    // parsing the GTFS entity here until we have identified which of the two this trip relates to (the PLANit trip will be
    // created while parsing stop_times (schedule based trip) and/or frequencies (frequency based trip)
    data.indexByGtfsTripId(gtfsTrip);
  }



}
