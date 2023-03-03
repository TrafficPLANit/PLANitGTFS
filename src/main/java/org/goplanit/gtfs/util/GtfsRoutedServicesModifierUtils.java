package org.goplanit.gtfs.util;

import org.goplanit.gtfs.converter.service.handler.GtfsServicesHandlerData;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.service.routed.modifier.event.handler.SyncDeparturesXmlIdToIdHandler;
import org.goplanit.service.routed.modifier.event.handler.SyncRoutedServicesXmlIdToIdHandler;
import org.goplanit.service.routed.modifier.event.handler.SyncRoutedTripsXmlIdToIdHandler;
import org.goplanit.utils.service.routed.RoutedServicesLayer;
import org.goplanit.utils.service.routed.modifier.RoutedServicesModifierListener;

import java.util.List;
import java.util.function.Consumer;

/**
 * Some utilities on modifying the routed services as it is convenient for the GTFS parser
 *
 */
public class GtfsRoutedServicesModifierUtils {

  /**
   * Convenience method to register unregister listeners on each found layer and apply the consumer provided
   *
   * @param consumer to execute on each layer
   * @param listeners to register and unregister before and after consumer is executed
   * @param routedServices to work on
   */
  private static void forEachLayerRegisterListenersAndApply(Consumer<RoutedServicesLayer> consumer, List<RoutedServicesModifierListener> listeners, RoutedServices routedServices) {
    for( var layer : routedServices.getLayers()){
      if(listeners != null) {
        listeners.forEach(l -> layer.getLayerModifier().addListener(l));
      }

      /* execute with listeners in place */
      consumer.accept(layer);

      if(listeners != null) {
        listeners.forEach(l -> layer.getLayerModifier().removeListener(l));
      }
    }
  }

  /**
   * PLANit routed services trips that have identical relative schedules (but different departure times) will be grouped together rather than continue to exist as separate trips.
   * <p>
   *   In case anything else uses the ids or XML ids of routed trips or their departures, handlers should be created that are called back when these ids change
   *   during the course of this method, since the ids will be recreated and many PLANit trips might get removed
   * </p>
   *
   * @param routedServices  to apply to for across all its layers
   */
  public static void groupIdenticallyScheduledPlanitTrips(RoutedServices routedServices) {

    Consumer<RoutedServicesLayer> consolidationLambda = layer -> {
      for (var mode : layer.getSupportedModesWithServices()) {
        layer.getLayerModifier().consolidateIdenticallyScheduledTrips(mode);
      }
    };

    /* perform consolidation per mode*/
    forEachLayerRegisterListenersAndApply(consolidationLambda, null, routedServices);

    Consumer<RoutedServicesLayer> recreateIdsLambda = layer -> {
      layer.getLayerModifier().recreateRoutedTripsIds();
    };

    /* recreate ids and sync XML ids */
    forEachLayerRegisterListenersAndApply(recreateIdsLambda, List.of(new SyncRoutedTripsXmlIdToIdHandler()), routedServices);

  }

  /**
   * Given that due to time period and day based filtering it is possible some GTFS trips only end up with a single stop, which means
   * they have no valid leg timings. This method can be used to clean up, i.e., remove those trips (and we recreate their underlying ids)
   *
   * @param routedServices to prune
   */
  public static void removeScheduledTripsWithoutLegs(RoutedServices routedServices) {

    Consumer<RoutedServicesLayer> removeTripsWithoutLegsOnLayerLambda = layer -> {
      var supportedModes = layer.getSupportedModes();
      supportedModes.forEach(mode -> layer.getLayerModifier().removeScheduledTripsWithoutLegs(true, mode));
    };

    /* we only remove trips here, so only trip XML ids are synced*/
    forEachLayerRegisterListenersAndApply(
        removeTripsWithoutLegsOnLayerLambda, List.of(new SyncRoutedTripsXmlIdToIdHandler(), new SyncDeparturesXmlIdToIdHandler()), routedServices);

  }

  /**
   * Given that due to time period and day based filtering it is likely that GTFS routes triggered creation of PLANit services
   * which eventually did not get mapped to any eligible trips in which case, this method can be used to clean up, i.e., remove
   * those services (and we recreate their underlying ids)
   *
   * @param routedServices to prune
   */
  public static void removeServiceRoutesWithoutTrips(RoutedServices routedServices) {

    Consumer<RoutedServicesLayer> removeServiceRoutesWithoutTripsOnLayerLambda = layer -> {
      var supportedModes = layer.getSupportedModes();
      supportedModes.forEach( mode -> layer.getLayerModifier().removeRoutedServicesWithoutTrips(true, mode));
    };

    forEachLayerRegisterListenersAndApply(removeServiceRoutesWithoutTripsOnLayerLambda, List.of(new SyncRoutedServicesXmlIdToIdHandler()), routedServices);

  }

  /**
   * In case any layers end up with no services, we can remove these layers. This is done here
   *
   * @param routedServices to prune
   */
  public static void removeEmptyRoutedServices(RoutedServices routedServices) {

    /* we only remove empty routed services here, so only routed services XML ids are synced*/
    forEachLayerRegisterListenersAndApply(
        l -> l.getLayerModifier().removeEmptyRoutedServicesByMode(true),
        List.of(new SyncRoutedServicesXmlIdToIdHandler()),
        routedServices);
  }

  /**
   * In case trips have duplicate departures, we can remove these departures. This is done here
   *
   * @param routedServices to prune
   */
  public static void removeDuplicateTripDepartures(RoutedServices routedServices) {

    /* we only remove duplicate departure, so only departure XML ids are synced*/
    forEachLayerRegisterListenersAndApply(
        l -> l.getLayerModifier().removeDuplicateTripDepartures(true),
        List.of(new SyncDeparturesXmlIdToIdHandler()),
        routedServices);
  }
}
