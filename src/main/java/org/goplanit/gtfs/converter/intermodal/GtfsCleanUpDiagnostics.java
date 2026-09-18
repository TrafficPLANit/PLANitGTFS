package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityIssue;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityType;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.ExternalIdAble;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.service.routed.RoutedService;
import org.goplanit.utils.service.routed.RoutedTripDeparture;
import org.goplanit.utils.service.routed.RoutedTripSchedule;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Records what the clean-up steps that align the parsed services with the physical network take away again.
 * <p>
 * What each step removes is established by comparing the entities present before it ran with those present after,
 * rather than by the step reporting it itself, the steps residing in the PLANit core where GTFS has no place. The
 * entities are compared by identity because a removal is followed by a renumbering of what survives
 * </p>
 *
 * @author markr
 */
class GtfsCleanUpDiagnostics {

  /** to record what is removed into */
  private final GtfsPlanitEntityDiagnostics diagnostics;

  /**
   * Create a set that holds entities by identity rather than by id, which changes when survivors are renumbered
   *
   * @param <E> type of entity
   * @return created set
   */
  private static <E> Set<E> createIdentitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  /**
   * Apply the given action to every routed service across all layers and modes
   *
   * @param routedServices to traverse
   * @param action to apply
   */
  private static void forEachRoutedService(
      final RoutedServices routedServices, final Consumer<RoutedService> action) {
    for (var layer : routedServices.getLayers()) {
      for (var modeServices : layer) {
        modeServices.forEach(action);
      }
    }
  }

  /**
   * Constructor
   *
   * @param diagnostics to record what is removed into
   */
  GtfsCleanUpDiagnostics(final GtfsPlanitEntityDiagnostics diagnostics) {
    this.diagnostics = diagnostics;
  }

  /**
   * Collect the service nodes present across all layers
   *
   * @param serviceNetwork to collect from
   * @return service nodes present
   */
  static Set<ServiceNode> collectServiceNodes(final ServiceNetwork serviceNetwork) {
    Set<ServiceNode> serviceNodes = createIdentitySet();
    serviceNetwork.getTransportLayers().forEach(layer -> layer.getServiceNodes().forEach(serviceNodes::add));
    return serviceNodes;
  }

  /**
   * Collect the schedule based trips present across all layers and modes
   *
   * @param routedServices to collect from
   * @return trip schedules present
   */
  static Set<RoutedTripSchedule> collectTripSchedules(final RoutedServices routedServices) {
    Set<RoutedTripSchedule> tripSchedules = createIdentitySet();
    forEachRoutedService(
        routedServices, service -> service.getTripInfo().getScheduleBasedTrips().forEach(tripSchedules::add));
    return tripSchedules;
  }

  /**
   * Collect the departures of the schedule based trips present across all layers and modes
   *
   * @param routedServices to collect from
   * @return departures present
   */
  static Set<RoutedTripDeparture> collectDepartures(final RoutedServices routedServices) {
    Set<RoutedTripDeparture> departures = createIdentitySet();
    forEachRoutedService(routedServices, service -> service.getTripInfo().getScheduleBasedTrips().forEach(
        tripSchedule -> tripSchedule.getDepartures().forEach(departures::add)));
    return departures;
  }

  /**
   * Record how many entities of a type a clean-up step was presented with, how many it left behind, and which ones it
   * took away.
   * <p>
   * A removed entity is recorded under its PLANit id, which is what the surrounding log speaks in, with its ids in
   * full as the detail that accompanies it into a listing
   * </p>
   *
   * @param <E> type of entity
   * @param entityType concerned
   * @param issue the step removes the entities under
   * @param before entities present before the step ran
   * @param after entities present after the step ran
   */
  <E extends ExternalIdAble> void registerRemoved(
      final GtfsPlanitEntityType entityType, final GtfsPlanitEntityIssue issue,
      final Set<E> before, final Set<E> after) {
    diagnostics.registerDesired(entityType, before.size());
    diagnostics.registerCreated(entityType, after.size());
    before.stream().filter(entity -> !after.contains(entity)).forEach(
        entity -> diagnostics.registerIssue(
            issue, String.valueOf(entity.getId()), entity.getIdsAsString()));
  }
}
