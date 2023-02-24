package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.converter.service.GtfsServicesHandlerProfiler;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.entity.GtfsRoute;
import org.goplanit.gtfs.entity.GtfsTrip;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.misc.CustomIndexTracker;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.service.routed.RoutedService;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.service.routed.RoutedServicesLayer;
import org.goplanit.utils.service.routed.RoutedTripSchedule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Track data used during handling/parsing of GTFS routes
 */
public class GtfsServicesHandlerData {

  /** reason for discarding trips, used during registering them */
  public enum TripDiscardType{
    ROUTE_DISCARDED,
    SERVICE_ID_DISCARDED;
  }

  // EXOGENOUS DATA TRACKING/SETTINGS

  /** settings to make available */
  private final GtfsServicesReaderSettings settings;

  /** profiler stats to update across applying of various handlers that use this data instance */
  private final GtfsServicesHandlerProfiler handlerProfiler;

  // LOCAL DATA TRACKING

  /** track activated service ids, indicating which days trips are to be parsed */
  Set<String> activeGtfsServiceIds = new HashSet<>();

  /** track all data mappings using a single 1:1 mapping*/
  CustomIndexTracker customIndexTracker;

  /** track which routes have been discarded by mode, to ensure we do not log warnings for correctly ignored GTFS routes */
  Map<String, RouteType> modeDiscardedRoutes;

  /** track which trips have been discarded based on discard type */
  Map<TripDiscardType, Set<String>> discardedTrips;

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

    activeGtfsServiceIds = new HashSet<>();
    customIndexTracker = new CustomIndexTracker();

    /* track routed service entries by external id (GTFS ROUTE_ID) */
    customIndexTracker.initialiseEntityContainer(RoutedService.class, (routedService) -> routedService.getExternalId());
    /* track GTFS trip entries by trip id (GTFS TRIP_ID) */
    customIndexTracker.initialiseEntityContainer(GtfsTrip.class, (gtfsTrip) -> gtfsTrip.getTripId());
    /* track PLANit scheduled trip entries by its external id (GTFS TRIP_ID) */
    customIndexTracker.initialiseEntityContainer(RoutedTripSchedule.class, (planitScheduledTrip) -> planitScheduledTrip.getExternalId());
    /* track PLANit service nodes by external id (GTFS STOP_ID) */
    customIndexTracker.initialiseEntityContainer(ServiceNode.class, getServiceNodeToGtfsStopIdMapping());

    modeDiscardedRoutes = new HashMap<>();
    discardedTrips = new HashMap<>();
  }

  /**
   * Constructor
   *
   * @param settings to use
   * @param serviceNetwork to use
   * @param routedServices to use
   * @param handlerProfiler to use
   */
  public GtfsServicesHandlerData(final GtfsServicesReaderSettings settings, final ServiceNetwork serviceNetwork, final RoutedServices routedServices, final GtfsServicesHandlerProfiler handlerProfiler){
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
   * @param planitRoutedService to register
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
   * Register GTFS route as discarded based on its route type (mode), which is a valid reason to
   * ignore it from further processing.
   *
   * @param gtfsRoute to mark as discarded
   */
  public void registeredDiscardedRoute(GtfsRoute gtfsRoute){
    this.modeDiscardedRoutes.put(gtfsRoute.getRouteId(), gtfsRoute.getRouteType());
  }

  /** Verify if GTFS route has been discarded based on its mode (route type) not being supported in this run
   *
   * @param gtfsRouteId to verify
   * @return true when discarded, false otherwise
   */
  public boolean isGtfsRouteDiscarded(String gtfsRouteId){
    return this.modeDiscardedRoutes.containsKey(gtfsRouteId);
  }

  /**
   * Register GTFS trip as discarded for a reason, e.g. because it route is discarded, see {@link #registeredDiscardedRoute(GtfsRoute)}, or because its service is not
   * registered for incusion, which are valid reasonsto ignore it from further processing without warning
   *
   * @param gtfsTrip to mark as discarded
   * @param type reason for discarding
   */
  public void registeredDiscardedTrip(GtfsTrip gtfsTrip, TripDiscardType type){
    var routeDiscardedTrips = this.discardedTrips.get(type);
    if(routeDiscardedTrips==null){
      routeDiscardedTrips = new HashSet<>();
      this.discardedTrips.put(type, routeDiscardedTrips);
    }
    routeDiscardedTrips.add(gtfsTrip.getTripId());
  }

  /** Verify if GTFS trip has been discarded based on some reason in this run
   *
   * @param gtfsTripId to verify
   * @return true when discarded, false otherwise
   */
  public boolean isGtfsTripDiscarded(String gtfsTripId){
    return this.discardedTrips.entrySet().stream().filter( e -> e.getValue().contains(gtfsTripId)).findFirst().isPresent();
  }

  /**
   * Register all active service ids, which will be cross-referenced with parsed trips. Only trips with an actve service id
   * should be parsed
   *
   * @param gtfsServiceId to register
   */
  public void registerActiveServiceId(String gtfsServiceId) {
    this.activeGtfsServiceIds.add(gtfsServiceId);
  }

  /**
   * Verify if any service ids have been activated
   *
   * @return true when present, false otherwise
   */
  public boolean hasActiveServiceIds() {
    return !this.activeGtfsServiceIds.isEmpty();
  }

  /**
   * Verify if a service id have been activated
   *
   * @return true when present, false otherwise
   */
  public boolean isActiveServiceId(String serviceId) {
    return this.activeGtfsServiceIds.contains(serviceId);
  }

  /**
   * Index the service node by its external id (GTFS_STOP_ID)
   * @param planitServiceNode to register
   */
  public void indexByExternalId(ServiceNode planitServiceNode) {
    customIndexTracker.register(ServiceNode.class, planitServiceNode);
  }

  /**
   * Collect routed service by external id index
   *
   * @param externalId to collect by
   * @return found routed service
   */
  public ServiceNode getServiceNodeByExternalId(String externalId) {
    return customIndexTracker.get(ServiceNode.class, externalId);
  }

  /**
   * Index the GTFS trip by its trip id (GTFS_TRIP_ID)
   * @param gtfsTrip to register
   */
  public void indexByGtfsTripId(GtfsTrip gtfsTrip) {
    customIndexTracker.register(GtfsTrip.class, gtfsTrip);
  }

  /**
   * Collect GTFS trip by GTFS trip id
   *
   * @param gtfsTripId to collect by
   * @return found GTFS trip entity
   */
  public GtfsTrip getGtfsTripByGtfsTripId(String gtfsTripId) {
    return customIndexTracker.get(GtfsTrip.class, gtfsTripId);
  }

  /**
   * Index the PLANit schedule based trip by its external id (GTFS_TRIP_ID)
   * @param planitScheduleBasedTrip to register
   */
  public void indexByExternalId(RoutedTripSchedule planitScheduleBasedTrip) {
    customIndexTracker.register(RoutedTripSchedule.class, planitScheduleBasedTrip);
  }

  /**
   * Collect Planit scheduled trip by its external id
   *
   * @param externalId to collect PLANit trip for
   * @return found schedule based PLANit trip (null if not present)
   */
  public RoutedTripSchedule getPlanitScheduleBasedTripByExternalId(String externalId) {
    return customIndexTracker.get(RoutedTripSchedule.class, externalId);
  }

  /**
   * GTFS Services are ingested and lead to PLANit service nodes to be created based on GTFS stop ids. When at some later point in time
   * these PLANit service nodes are to be linked to PLANit transfer zones (which in turn have an association with a GTFS stop) the mapping
   * between PLANit service node and its underlying GTFS stop needs to remain available. This function provides this mapping.
   * <p>
   *   For now this mapping is purely based on the external id, but if this changes using this explicit functional approach allows
   *   us to change this without having to change the process flow itself
   * </p>
   *
   * @return mapping from PLANit service node to underlying source GTFS stop id
   */
  public static Function<ServiceNode, String> getServiceNodeToGtfsStopIdMapping(){
    return ServiceNode::getExternalId;
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
  public GtfsServicesHandlerProfiler getProfiler() {
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
