package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedService;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.service.routed.RoutedServicesLayer;
import org.goplanit.utils.misc.CustomIndexTracker;
import org.goplanit.utils.mode.Mode;

import java.util.HashMap;
import java.util.Map;

/**
 * Track data used during handling/parsing of GTFS routes
 */
public class GtfsFileHandlerData {

  /** index routed services by mode */
  Map<Mode, RoutedServicesLayer> routedServiceLayerByMode;

  /** service network to populate */
  final ServiceNetwork serviceNetwork;

  /** routed service to populate (indirectly via mode indexed {@link #routedServiceLayerByMode}) */
  final RoutedServices routedServices;

  /** track all mapping from GTFS ids to PLANit entities */
  CustomIndexTracker customIndexTracker;

  /**
   * Initialise the tracking of data by indexing the layers by mode as well as creating PLANit entity based custom indices
   * that match the GTFS indices for quick lookups
   *
   * @param routedServices to use
   */
  private void initialise(final RoutedServices routedServices){
    /* lay indices by mode -> routedServicesLayer */
    routedServiceLayerByMode = routedServices.getLayers().indexLayersByMode();

    /* track routed service entries by external id (GTFS ROUTE_ID) */
    customIndexTracker.initialiseEntityContainer(RoutedService.class, (routedService) -> routedService.getExternalId());
  }

  /**
   * Constructor
   */
  public GtfsFileHandlerData(final ServiceNetwork serviceNetwork, final RoutedServices routedServices){
    this.serviceNetwork = serviceNetwork;
    this.routedServices = routedServices;
    customIndexTracker = new CustomIndexTracker();

    initialise(routedServices);
  }

  RoutedServicesLayer getRoutedServicesLayer(Mode mode){
    return routedServiceLayerByMode.get(mode);
  }

  /**
   * Index the routed service by its external id (GTFS_ROUTE_ID)
   * @param planitRoutedService
   */
  public void indexByExternalId(RoutedService planitRoutedService) {
    customIndexTracker.register(RoutedService.class, planitRoutedService);
  }

  /** Collect by external id index
   *
   * @param externalId to collect by
   */
  public void getRoutedServiceByExternalId(String externalId) {
    customIndexTracker.get(RoutedService.class, externalId);
  }

  /** Access to the service network
   * @return the service network being populated
   */
  public ServiceNetwork getServiceNetwork() {
    return serviceNetwork;
  }

  /** Access to the routed services container
   * @return the routed services  being populated
   */
  public RoutedServices getRoutedServices(){
    return this.routedServices;
  }
}
