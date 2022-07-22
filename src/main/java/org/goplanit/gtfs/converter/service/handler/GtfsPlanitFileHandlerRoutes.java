package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.converter.service.GtfsRoutesHandlerProfiler;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.entity.GtfsRoute;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.gtfs.handler.GtfsFileHandlerRoutes;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.network.layers.MacroscopicNetworkLayers;

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
  }

  /**
   * Handle a GTFS route
   */
  @Override
  public void handle(GtfsRoute gtfsRoute) {
    RouteType routeType = gtfsRoute.getRouteType();
    //TODO -> continue here convert to PLANit mode based on adopted mapping consistent with underlying network modes
  }

}
