package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.converter.service.GtfsRoutesHandlerProfiler;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.entity.GtfsRoute;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.gtfs.handler.GtfsFileHandlerRoutes;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedService;
import org.goplanit.service.routed.RoutedServices;
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

  /** profiler to use */
  private final GtfsRoutesHandlerProfiler profiler;

  /** service network to populate */
  private final ServiceNetwork serviceNetwork;

  /** routed services to populate */
  private final RoutedServices routedServices;

  /** settings containing configuration */
  private final GtfsServicesReaderSettings settings;

  /** track internal data used to efficiently handle the parsing */
  private final GtfsFileHandlerRoutesData data;

  /**
   * Initialise this handler
   */
  private void initialise(){
    PlanItRunTimeException.throwIf(routedServices.getParentNetwork() != serviceNetwork, "Routed services its service network does not match the service network provided");
    PlanItRunTimeException.throwIf(!serviceNetwork.getTransportLayers().isEmpty() && serviceNetwork.getTransportLayers().isEachLayerEmpty(), "Service network is expected to have been initialise with empty layers before populating with GTFS routes");
    PlanItRunTimeException.throwIf(!routedServices.getLayers().isEmpty() && routedServices.getLayers().isEachLayerEmpty(), "Routed services layers are expected to have been initialised empty when populating with GTFS routes");

    /* create a new service network layer for each physical layer that is present */
    settings.getReferenceNetwork().getTransportLayers().forEach(parentLayer -> serviceNetwork.getTransportLayers().getFactory().registerNew(parentLayer));

    /* create a routed services for each service layer that we created */
    serviceNetwork.getTransportLayers().forEach(parentLayer -> routedServices.getLayers().getFactory().registerNew(parentLayer));
  }

  /**
   * Constructor
   *
   * @param serviceNetworkToPopulate the PLANit service network to populate
   * @param routedServices           the PLANit routed services to populate
   * @param settings                 to apply where needed
   * @param profiler                 to use
   */
  public GtfsPlanitFileHandlerRoutes(final ServiceNetwork serviceNetworkToPopulate, RoutedServices routedServices, final GtfsServicesReaderSettings settings, final GtfsRoutesHandlerProfiler profiler) {
    super();
    this.serviceNetwork = serviceNetworkToPopulate;
    this.routedServices = routedServices;
    this.settings = settings;
    this.profiler = profiler;

    initialise();
    /* after initialise() because routed services must have appropriate layers available to initialise data */
    this.data = new GtfsFileHandlerRoutesData(routedServices);
  }

  /**
   * Handle a GTFS route
   */
  @Override
  public void handle(GtfsRoute gtfsRoute) {
    RouteType routeType = gtfsRoute.getRouteType();
    Mode planitMode = settings.getPlanitModeIfActivated(routeType);
    if(planitMode == null){
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
    /* service description -> GTFS rote_desc */
    if(gtfsRoute.hasRouteDescription()) {
      planitRoutedService.setServiceDescription(gtfsRoute.getRouteDescription());
    }

    /* index by GTFS route_id */
    data.register(gtfsRoute.getRouteId(), planitRoutedService);
    profiler.incrementRouteCount();
  }

}
