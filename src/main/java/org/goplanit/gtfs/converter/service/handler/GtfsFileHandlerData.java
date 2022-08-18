package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.converter.service.GtfsHandlerProfiler;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.entity.GtfsFrequency;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedService;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.service.routed.RoutedServicesLayer;
import org.goplanit.service.routed.RoutedTripDeparture;
import org.goplanit.utils.misc.CustomIndexTracker;
import org.goplanit.utils.mode.Mode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Track data used during handling/parsing of GTFS routes
 */
public class GtfsFileHandlerData {

  // EXOGENOUS DATA TRACKING/SETTINGS

  /** settings to make available */
  private final GtfsServicesReaderSettings settings;

  /** profiler stats to update across applying of various handlers that use this data instance */
  private final GtfsHandlerProfiler handlerProfiler;

  // LOCAL DATA TRACKING

  /** track all data mappings using a single 1:1 mapping*/
  CustomIndexTracker customIndexTracker;

  /** track the converted GTFS stop times (to PLANit RoutedTripDepartures) per trip */
  Map<String, List<RoutedTripDeparture>> gtfsTripIdToRoutedTripDepartures;

  /** index routed services by mode */
  Map<Mode, RoutedServicesLayer> routedServiceLayerByMode;

  // TO POPULATE

  /** service network to populate */
  final ServiceNetwork serviceNetwork;

  /** routed service to populate (indirectly via mode indexed {@link #routedServiceLayerByMode}) */
  final RoutedServices routedServices;


  /**
   * Initialise the tracking of data by indexing the layers by mode as well as creating PLANit entity based custom indices
   * that match the GTFS indices for quick lookups
   *
   * @param routedServices to use
   */
  private void initialise(final RoutedServices routedServices){
    /* lay indices by mode -> routedServicesLayer */
    routedServiceLayerByMode = routedServices.getLayers().indexLayersByMode();

    customIndexTracker = new CustomIndexTracker();

    /* track routed service entries by external id (GTFS ROUTE_ID) */
    customIndexTracker.initialiseEntityContainer(RoutedService.class, (routedService) -> routedService.getExternalId());
    /* track GTFS trip frequency entries by trip id (GTFS TRIP_ID) */
    customIndexTracker.initialiseEntityContainer(GtfsFrequency.class, (gtfsFrequency) -> gtfsFrequency.getTripId());
    /* track GTFS trip stop times by trip id (GTFS TRIP_ID) */
    gtfsTripIdToRoutedTripDepartures = new HashMap<>();
  }

  /**
   * Constructor
   *
   * @param settings to use
   * @param serviceNetwork to use
   * @param routedServices to use
   * @param handlerProfiler to use
   */
  public GtfsFileHandlerData(final GtfsServicesReaderSettings settings, final ServiceNetwork serviceNetwork, final RoutedServices routedServices, final GtfsHandlerProfiler handlerProfiler){
    this.serviceNetwork = serviceNetwork;
    this.routedServices = routedServices;
    this.settings = settings;
    this.handlerProfiler = handlerProfiler;

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

  /**
   * Collect routed service by external id index
   *
   * @param externalId to collect by
   * @return found routed service
   */
  public RoutedService getRoutedServiceByExternalId(String externalId) {
    return customIndexTracker.get(RoutedService.class, externalId);
  }

  /**
   * Collect GTFS frequency by trip id
   *
   * @param gtfsTripId to collect by
   * @return found frequency entry, or null if not present
   */
  public GtfsFrequency getFrequencyByGtfsTripId(String gtfsTripId) {
    return customIndexTracker.get(GtfsFrequency.class, gtfsTripId);
  }

  /**
   * Collect the PLANit departures associated with a given GTFS trip id (if any)
   *
   * @param gtfsTripId to collect for
   * @return found departures, null if none found
   */
  public List<RoutedTripDeparture> getRoutedTripDeparturesByGtfsTripId(String gtfsTripId) {
    return this.gtfsTripIdToRoutedTripDepartures.get(gtfsTripId);
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

  /**
   * Access to profiler
   *
   * @return profiler
   */
  public GtfsHandlerProfiler getProfiler() {
    return handlerProfiler;
  }

  /**
   * Access to GTFS services reader settings
   *
   * @return user configuration settings
   */
  public GtfsServicesReaderSettings getSettings() {
    return this.settings;
  }
}
