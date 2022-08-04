package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.service.routed.RoutedService;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.service.routed.RoutedServicesLayer;
import org.goplanit.utils.mode.Mode;

import java.util.HashMap;
import java.util.Map;

/**
 * Track data used during handling/parsing of GTFS routes
 */
public class GtfsFileHandlerRoutesData {

  /** index routed services by mode */
  Map<Mode, RoutedServicesLayer> routedServiceLayerByMode;

  Map<String, RoutedService> routedServiceByGtfsRouteId;

  private void initialise(final RoutedServices routedServices){
    /* lay indices by mode -> routedServicesLayer */
    routedServiceLayerByMode = routedServices.getLayers().indexLayersByMode();
  }

  /**
   * Constructor
   */
  public GtfsFileHandlerRoutesData(final RoutedServices routedServices){
    routedServiceLayerByMode = new HashMap<>();
    routedServiceByGtfsRouteId = new HashMap<>();
    initialise(routedServices);
  }

  RoutedServicesLayer getRoutedServicesLayer(Mode mode){
    return routedServiceLayerByMode.get(mode);
  }

  public void register(String gtfsRouteId, RoutedService planitRoutedService) {
    routedServiceByGtfsRouteId.put(gtfsRouteId, planitRoutedService);
  }
}
