package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.entity.GtfsRoute;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.gtfs.handler.GtfsFileHandlerRoutes;
import org.goplanit.utils.service.routed.RoutedService;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.mode.Mode;

import java.util.logging.Logger;

/**
 * Handler for handling routes and populating a PLANit (Service) network and routes with the found GTFS routes
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerRoutes extends GtfsFileHandlerRoutes {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerRoutes.class.getCanonicalName());

  /** track internal data used to efficiently handle the parsing */
  private final GtfsServicesHandlerData data;

  /**
   * Constructor
   *
   * @param gtfsServicesHandlerData      containing all data to track and resources needed to perform the processing
   */
  public GtfsPlanitFileHandlerRoutes(final GtfsServicesHandlerData gtfsServicesHandlerData) {
    super();
    this.data = gtfsServicesHandlerData;

    PlanItRunTimeException.throwIfNull(data.getRoutedServices(), "Routed services not present, unable to parse GTFS routes");
    PlanItRunTimeException.throwIfNull(data.getServiceNetwork(), "Services network not present, unable to parse GTFS routes");
  }

  /**
   * Handle a GTFS route
   */
  @Override
  public void handle(GtfsRoute gtfsRoute) {
    RouteType routeType = gtfsRoute.getRouteType();
    Mode planitMode = data.getSettings().getPlanitModeIfActivated(routeType);
    if(planitMode == null){
      data.registeredDiscardByUnsupportedRoute(gtfsRoute);
      return;
    }

    /* obtain correct routed services layer and its current known services for our mode */
    var layer = data.getRoutedServicesLayer(planitMode);
    var servicesPerMode = layer.getServicesByMode(planitMode);
    RoutedService planitRoutedService = servicesPerMode.getFactory().registerNew();

    /* XML id = internal id */
    planitRoutedService.setXmlId(planitRoutedService.getId());
    /* external id  = GTFS route_id */
    planitRoutedService.setExternalId(gtfsRoute.getRouteId());

    if(!gtfsRoute.hasValidName()){
      LOGGER.warning("GTFS route with id %s has no valid name (either long or short)");
    }

    /* name = GTFS short name */
    if(gtfsRoute.hasShortName()) {
      planitRoutedService.setName(gtfsRoute.getShortName());
    }
    /* nameDescription = GTFS long name */
    if(gtfsRoute.hasLongName()) {
      planitRoutedService.setNameDescription(gtfsRoute.getLongName());
    }
    /* service description -> GTFS route_desc */
    if(gtfsRoute.hasRouteDescription()) {
      planitRoutedService.setServiceDescription(gtfsRoute.getRouteDescription());
    }

    /* indexed by GTFS route_id */
    data.indexByExternalId(planitRoutedService);
    data.getProfiler().incrementRouteCount(gtfsRoute.getRouteType());
  }

}
