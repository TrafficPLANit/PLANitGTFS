package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.entity.GtfsStopTime;
import org.goplanit.gtfs.entity.GtfsTrip;
import org.goplanit.gtfs.handler.GtfsFileHandlerStopTimes;
import org.goplanit.gtfs.util.GtfsUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.service.routed.RoutedService;
import org.goplanit.utils.service.routed.RoutedTripSchedule;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.network.layer.ServiceNetworkLayer;
import org.goplanit.utils.network.layer.service.ServiceLeg;
import org.goplanit.utils.network.layer.service.ServiceLegSegment;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.time.ExtendedLocalTime;

import java.util.*;
import java.util.logging.Logger;

/**
 * Handler for handling GTFS stop times to populate PLANit service (network/routed services) memory model
 * <p>
 *   Prerequisites:
 *   (i) It is assumed routed services and service network are available and layers are initialised, (ii) it is assumed
 *   the sequence of stops per trip is increasing while traversing the file, i.e., a stop with sequence 1 will always be parsed before
 *   any other stop with sequence greater than 1.
 * </p>
 * <p>
 *   As part of this handler, we create schedule based trips as needed where we make no effort to consolidate trips that have the
 *   same schedule but different departure times yet, i.e., we create a single schedule based trip with a single departure and relative timings
 *   per chain of stop times. In other words a GTFS trip will correspond 1:1 to a PLANit trip after parsing
 * </p>
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerStopTimes extends GtfsFileHandlerStopTimes {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerStopTimes.class.getCanonicalName());

  /** track internal data used to efficiently handle the parsing */
  private final GtfsServicesHandlerData data;

  /** set of tracked GTFS route short names to log information on while parsing */
  private final Set<String> activatedLoggingForGtfsRoutesByShortName;

  /** track found GTFS routes for stop, if stop is indicated to be tracked for passing routes */
  private final Map<String, Set<String>> uniqueRoutesForStopsIfLoggingRequired = new HashMap<>();

  /** track previous entry's related GTFS trip - as for now we assume they will only be provided in consecutive order within the file,
   * If for a given GTFS file this is violated, we will need to have a more sophisticated way: either first parse all entries and then process in order
   * (memory intensive), or, on the fly change the PLANit memory model (departures, stop ordering). The latter seems a better approach)
   */
  private GtfsTrip prevStopTimeTrip;

  /**
   * Previous stop time entry to be able to construct leg segment on service network
   */
  private GtfsStopTime prevSameTripStopTime;

  /**
   * @return compare by ids and departure arrival time, when all equal, it is considered equal for our intents and purposes and true is returned, false otherwise
   */
  private boolean isConsideredEqual(GtfsStopTime left, GtfsStopTime right) {
    return
        left.getTripId().equals(right.getTripId()) &&
        left.getStopId().equals(right.getStopId()) &&
        left.getDepartureTime().equals(right.getDepartureTime()) &&
        left.getArrivalTime().equals(right.getArrivalTime());
  }

  /**
   * Collect or created a routed scheduled PLANit trip to populate
   *
   * @param gtfsTrip to use and find/create PLANit trip
   * @param planitRoutedService to use and find/create PLANit trip
   * @return PLANit route scheduled trip instance
   */
  private RoutedTripSchedule collectScheduledTrip(GtfsTrip gtfsTrip, RoutedService planitRoutedService) {
    RoutedTripSchedule planitTrip = data.getPlanitScheduleBasedTripByExternalId(gtfsTrip.getTripId());
    if(planitTrip == null){
      var tripsForService = planitRoutedService.getTripInfo();
      planitTrip = tripsForService.getScheduleBasedTrips().getFactory().registerNew();

      /* XML id */
      planitTrip.setXmlId(planitTrip.getId());

      /* external id = GTFS trip id*/
      planitTrip.setExternalId(gtfsTrip.getTripId());
      data.indexByExternalId(planitTrip);
      data.getProfiler().incrementScheduledTripCount();
    }

    return planitTrip;
  }

  /**
   * Register a new departure for the planit trip
   *
   * @param planitTrip to use
   * @param gtfsStopTime to extract info from
   * @param departureTime departure time
   */
  private void registerDeparture(RoutedTripSchedule planitTrip, GtfsStopTime gtfsStopTime, ExtendedLocalTime departureTime) {
    var departure = planitTrip.getDepartures().getFactory().registerNew(departureTime);

    /* XML id */
    departure.setXmlId(departure.getId());

  }

  /**
   * register a new service node on the service network for the given stop (without physical parent node) if
   * it did not exist already
   *
   * @param layer        to register on
   * @param gtfsStopTime to extract information for service node for
   * @return created or found service node
   */
  private ServiceNode collectOrRegisterServiceNode(ServiceNetworkLayer layer, GtfsStopTime gtfsStopTime) {
    var currServiceNode = data.getServiceNodeByExternalId(gtfsStopTime.getStopId());
    if(currServiceNode == null){
      currServiceNode = layer.getServiceNodes().getFactory().registerNew();

      /* XML id -> PLANit id */
      currServiceNode.setXmlId(currServiceNode.getId());
      /* external id -> GTFS stop id */
      currServiceNode.setExternalId(gtfsStopTime.getStopId());

      /* index by external id for later lookups */
      data.indexByExternalId(currServiceNode);
    }
    return currServiceNode;
  }

  /**
   * Based on the current stop time and state (previous stop) construct a new, or find and existing, service leg segment on the service
   * network that is to be used to route the trip over. Create service node for end point of leg segment if need be based on GTFS_STOP_ID
   *
   * @param layer to use
   * @param serviceModeForStop the mode of the routed service that is visiting this stop
   * @param gtfsStopTime to use
   * @return found or created service leg segment
   */
  private ServiceLegSegment collectOrRegisterNetworkServiceSegment(ServiceNetworkLayer layer, Mode serviceModeForStop, GtfsStopTime gtfsStopTime) {
    /* service nodes */
    var prevServiceNode = data.getServiceNodeByExternalId(prevSameTripStopTime.getStopId());
    /* service node registered by GTFS_STOP_ID */
    var currServiceNode = collectOrRegisterServiceNode(layer, gtfsStopTime);

    /* service segment */
    var serviceNetworkSegment = prevServiceNode.getLegSegment(currServiceNode);
    if(serviceNetworkSegment!= null && !this.data.getServiceLegMode(serviceNetworkSegment.getParent()).equals(serviceModeForStop)){
      /* service leg is attributed to a different mode, so likely requires different infrastructure later on, e.g., bus vs tram
       * operating on same GTFS stop. Therefore, create a separate service leg(segment) and attribute to this new mode
       * as wel will map different (physical) paths between the stops based on this mode separation */
      serviceNetworkSegment = null;
    }

    if(serviceNetworkSegment == null){
      ServiceLeg parentLeg = null;
      boolean dirPrevCur = true;
      var oppositeDirLegSegment = currServiceNode.getLegSegment(prevServiceNode);
      if(oppositeDirLegSegment == null){
        parentLeg = layer.getLegs().getFactory().registerNew(prevServiceNode, currServiceNode, true);
        parentLeg.setXmlId(parentLeg.getId());
        /* external id: vertAExtId_vertBExtId */
        parentLeg.setExternalId(parentLeg.getVertexA().getExternalId()+"_"+parentLeg.getVertexB().getExternalId());
      }else{
        parentLeg = oppositeDirLegSegment.getParent();
        dirPrevCur = false;
      }
      serviceNetworkSegment = layer.getLegSegments().getFactory().registerNew(parentLeg, dirPrevCur, true);
      serviceNetworkSegment.setXmlId(serviceNetworkSegment.getId());
      /* external id: vertUpstrExtId_vertDownstrExtId */
      serviceNetworkSegment.setExternalId(
          dirPrevCur ? serviceNetworkSegment.getUpstreamServiceNode().getExternalId()+"_"+serviceNetworkSegment.getDownstreamServiceNode().getExternalId() :
              serviceNetworkSegment.getDownstreamServiceNode().getExternalId()+"_"+serviceNetworkSegment.getUpstreamServiceNode().getExternalId());
      // attach mode to service leg until we create physical link segment mapping (with the same mode)
      data.registerServiceLegMode(parentLeg, serviceModeForStop);
    }
    return serviceNetworkSegment;
  }

  /**
   * Constructor
   *
   * @param gtfsServicesHandlerData                  containing all data to track and resources needed to perform the processing
   */
  public GtfsPlanitFileHandlerStopTimes(final GtfsServicesHandlerData gtfsServicesHandlerData) {
    this(gtfsServicesHandlerData, new HashSet<>());
  }

  /**
   * Constructor
   *
   * @param gtfsServicesHandlerData                  containing all data to track and resources needed to perform the processing
   * @param activatedLoggingForGtfsRoutesByShortName set of tracked GTFS routes (by short name) to log information for found in the dataset
   */
  public GtfsPlanitFileHandlerStopTimes(final GtfsServicesHandlerData gtfsServicesHandlerData, Set<String> activatedLoggingForGtfsRoutesByShortName) {
    super();
    this.data = gtfsServicesHandlerData;
    this.activatedLoggingForGtfsRoutesByShortName = activatedLoggingForGtfsRoutesByShortName;

    PlanItRunTimeException.throwIfNull(data.getRoutedServices(), "Routed services not present, unable to parse GTFS stop times");
    PlanItRunTimeException.throwIfNull(data.getServiceNetwork(), "Services network not present, unable to parse GTFS stop times");
    // prerequisites
    PlanItRunTimeException.throwIf(data.getRoutedServices().getLayers().isEachLayerEmpty(),"No GTFS routes parsed yet, unable to parse GTFS stop times");

    reset();
  }

  /**
   * Handle a GTFS stop time for a given trip
   */
  @Override
  public void handle(GtfsStopTime gtfsStopTime) {

    if(data.isGtfsTripRemoved(gtfsStopTime.getTripId())) {
      return;
    }

    /* PREP */
    GtfsTrip gtfsTrip = data.getGtfsTripByGtfsTripId(gtfsStopTime.getTripId());
    if(gtfsTrip == null){
      //LOGGER.severe(String.format("Unable to find GTFS trip %s for current GTFS stop time (stop id: %s), GTFS stop time ignored", gtfsStopTime.getTripId(), gtfsStopTime.getStopId()));
      return;
    }

    var planitRoutedService = data.getRoutedServiceByExternalId(gtfsTrip.getRouteId());
    if(planitRoutedService == null){
      LOGGER.severe(String.format("Unable to find GTFS route %s in PLANit memory model corresponding to GTFS trip %s, GTFS stop time (stop id %s) ignored", gtfsTrip.getRouteId(), gtfsTrip.getTripId(), gtfsStopTime.getStopId()));
      return;
    }
    boolean logTrackedRoute = (activatedLoggingForGtfsRoutesByShortName.contains(planitRoutedService.getName()));
    var layer = data.getServiceNetwork().getLayerByMode(planitRoutedService.getMode());

    /* change of GTFS trip between stop times, assume current stop time is the very first stop time for the new trip */
    boolean isTripDepartureTime = false;
    if(gtfsTrip != prevStopTimeTrip){
      prevSameTripStopTime = null;
      isTripDepartureTime = true;
    }

    /* STOP_TIME - Arrival time/departure time */
    ExtendedLocalTime arrivalTime = GtfsUtils.parseGtfsTime(gtfsStopTime.getArrivalTime());
    ExtendedLocalTime departureTime = GtfsUtils.parseGtfsTime(gtfsStopTime.getDepartureTime());

    /* verify if departure time of this trip falls within eligible time window, if not and we do not allow for partial trips, discard the trip fully */
    if(isTripDepartureTime && !data.isDepartureTimeOfServiceIdWithinEligibleTimePeriod(gtfsTrip.getServiceId(), departureTime)){
      /* outside time period of interest for any day the trip runs, do not parse, unless maybe later stops fall in time windows and we want to check that */
      if(!data.getSettings().isIncludePartialGtfsTripsIfStopsInTimePeriod()) {
        data.registeredRemovedGtfsTrip(gtfsTrip, GtfsServicesHandlerData.TripRemovalType.TIME_PERIOD_DISCARDED);
      }
      return;
    }

    /* GTFS may contain virtually identical entries in terms of arrival departure times for the same trip and stop. These are filtered here */
    if(!isTripDepartureTime && prevSameTripStopTime!= null && isConsideredEqual(gtfsStopTime, prevSameTripStopTime)){
      data.getProfiler().incrementDuplicateStopTimeCount();
      return;
    }

    /* SCHEDULED TRIP */
    RoutedTripSchedule planitTrip = collectScheduledTrip(gtfsTrip, planitRoutedService);

    /* STOP_TIME - INITIAL DEPARTURE */
    if(planitTrip.getDepartures().isEmpty()){
      registerDeparture(planitTrip, gtfsStopTime, departureTime);
      /* service node registered by GTFS_STOP_ID */
      collectOrRegisterServiceNode(layer, gtfsStopTime);
    }
    /* STOP_TIME - INTERMEDIATE STOP */
    else{
      if(prevStopTimeTrip == null){
        LOGGER.severe(String.format("GTFS trip's stop times not consecutive for GTFS trip %s, GTFS parser does not yet support such stop_time files, log GitHub feature request!",gtfsStopTime.getTripId()));
        return;
      }

      /* TIMING BETWEEN STOP and PREV STOP + SERVICE NETWORK UPDATE IF NEEDED (service node by GTFS_STOP_ID) */
      var serviceNetworkSegment = collectOrRegisterNetworkServiceSegment(layer, planitRoutedService.getMode(), gtfsStopTime);
      var duration = arrivalTime.minus(GtfsUtils.parseGtfsTime(prevSameTripStopTime.getDepartureTime()));
      var dwellTime = departureTime.minus(arrivalTime);
      if(duration.exceedsSingleDay() || dwellTime.exceedsSingleDay()){
        LOGGER.severe(String.format("Duration (%s) between stops (%s, %s) and/or dwell time at stop (%s) should be less than a day, ignored",
                duration, serviceNetworkSegment.getUpstreamServiceNode().getExternalId(), serviceNetworkSegment.getDownstreamServiceNode().getExternalId(), dwellTime));
        return;
      }
      planitTrip.addRelativeLegSegmentTiming(serviceNetworkSegment, duration.asLocalTimeBeforeMidnight(), dwellTime.asLocalTimeBeforeMidnight());
    }

    if(logTrackedRoute){
      LOGGER.info(String.format("[TRACK] stop: %s, trip: %s, route: %s (%s), arrival--departure: %s -- %s",
          gtfsStopTime.getStopId(), planitTrip.getExternalId(), planitRoutedService.getName(), planitRoutedService.getNameDescription(), arrivalTime, departureTime));
    }
    if(data.getSettings().isLogGtfsStopRoute(gtfsStopTime.getStopId())){
      uniqueRoutesForStopsIfLoggingRequired.putIfAbsent(gtfsStopTime.getStopId(), new TreeSet<>());
      uniqueRoutesForStopsIfLoggingRequired.get(gtfsStopTime.getStopId()).add("(name: " + planitRoutedService.getName()+" id: " + gtfsTrip.getRouteId()+")");
    }

    data.getProfiler().incrementTripStopTimeCount();

    this.prevSameTripStopTime = gtfsStopTime;
    this.prevStopTimeTrip = gtfsTrip;
  }

  /**
   * Reset
   */
  @Override
  public void reset(){
    prevSameTripStopTime = null;
    prevStopTimeTrip = null;
    uniqueRoutesForStopsIfLoggingRequired.clear();
  }

  /**
   * All GTFS routes per tracked stop that were found to require user logging
   *
   * @return result
   */
  public Map<String, Set<String>> getUniqueRoutesForTrackedGtfsStops(){
    return uniqueRoutesForStopsIfLoggingRequired;
  }

}
