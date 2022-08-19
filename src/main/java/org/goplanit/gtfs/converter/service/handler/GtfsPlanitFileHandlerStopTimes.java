package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.entity.GtfsStopTime;
import org.goplanit.gtfs.entity.GtfsTrip;
import org.goplanit.gtfs.handler.GtfsFileHandlerStopTimes;
import org.goplanit.gtfs.util.GtfsUtils;
import org.goplanit.service.routed.RoutedService;
import org.goplanit.service.routed.RoutedTripSchedule;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.network.layer.ServiceNetworkLayer;
import org.goplanit.utils.network.layer.service.ServiceLeg;
import org.goplanit.utils.network.layer.service.ServiceLegSegment;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.time.ExtendedLocalTime;

import java.util.logging.Logger;

/**
 * Handler for handling GTFS stop times to populate PLANit service (network/routed services) memory model
 * <p>
 *   Prerequisites:
 *   (i) It is assumed routed services and service network are available and layers are initialised, (ii) it is assumed
 *   the sequence of stops per trip is increasing while traversing the file, i.e., a stop with sequence 1 will always be parsed before
 *   any other stop with sequence > 1.
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
  private final GtfsFileHandlerData data;

  /** track previous entry's related GTFS trip - as for now we assume they will only be provided in consecutive order within the file,
   * If for a given GTFS file this is violated, we will need to have a more sophisticated way: either first parse all entries and then process in order
   * (memory intesnive), or, on the fly change the PLANit memory model (departures, stop ordering). The latter seems a better approach)
   */
  private GtfsTrip prevStopTimeTrip;

  /**
   * Previous stop time entry to be able to construct leg segment on service network
   */
  private GtfsStopTime prevSameTripStopTime;

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
    if(departureTime.exceedsSingleDay()){
      LOGGER.severe(String.format("Departure time of trip should never be beyond midnight (%s), in that case it should be early in the morning (trip %s), ignore stop time entry",gtfsStopTime.getTripId(), departureTime));
      return;
    }

    var departure = planitTrip.getDepartures().getFactory().registerNew(departureTime.asLocalTimeBeforeMidnight());

    /* XML id */
    departure.setXmlId(departure.getId());

    /* external id --> stop_sequence */
    departure.setExternalId(gtfsStopTime.getStopSequence());
  }

  /**
   * register a new service node on the service network for the given stop (without physical parent node) if
   * it did not exist already
   *
   * @param layer        to register on
   * @param gtfsStopTime to extract information for service node for
   * @return created or found service node
   */
  private ServiceNode registerServiceNode(ServiceNetworkLayer layer, GtfsStopTime gtfsStopTime) {
    var currServiceNode = data.getServiceNodeByExternalId(gtfsStopTime.getStopId());
    if(currServiceNode == null){
      currServiceNode = layer.getServiceNodes().getFactory().registerNew(null);
    }
    return currServiceNode;
  }

  /**
   * Based on the current stop time and state (previous stop) construct a new, or find and existing, service leg segment on the service
   * network that is to be used to route the trip over
   *
   * @param layer to use
   * @param gtfsStopTime to use
   * @return found or created service leg segment
   */
  private ServiceLegSegment collectOrRegisterNetworkServiceSegment(ServiceNetworkLayer layer, GtfsStopTime gtfsStopTime) {
    /* service nodes */
    var prevServiceNode = data.getServiceNodeByExternalId(prevSameTripStopTime.getStopId());
    var currServiceNode = registerServiceNode(layer, gtfsStopTime);

    /* service segment */
    var serviceNetworkSegment = prevServiceNode.getLegSegment(currServiceNode);
    if(serviceNetworkSegment == null){
      ServiceLeg parentLeg = null;
      boolean dirPrevCur = true;
      var oppositeDirLegSegment = currServiceNode.getLegSegment(prevServiceNode);
      if(oppositeDirLegSegment == null){
        parentLeg = layer.getLegs().getFactory().registerNew(prevServiceNode, currServiceNode, true);
      }else{
        parentLeg = oppositeDirLegSegment.getParent();
        dirPrevCur = false;
      }
      serviceNetworkSegment = layer.getLegSegments().getFactory().registerNew(parentLeg, dirPrevCur, true);
    }
    return serviceNetworkSegment;
  }

  /**
   * Constructor
   *
   * @param gtfsFileHandlerData      containing all data to track and resources needed to perform the processing
   */
  public GtfsPlanitFileHandlerStopTimes(final GtfsFileHandlerData gtfsFileHandlerData) {
    super();
    this.data = gtfsFileHandlerData;

    PlanItRunTimeException.throwIfNull(data.getRoutedServices(), "Routed services not present, unable to parse GTFS stop times");
    PlanItRunTimeException.throwIfNull(data.getServiceNetwork(), "Services network not present, unable to parse GTFS stop times");
    // prerequisites
    PlanItRunTimeException.throwIf(data.getRoutedServices().getLayers().isEachLayerEmpty()==true,"No GTFS routes parsed yet, unable to parse GTFS stop times");

    reset();
  }

  /**
   * Handle a GTFS stop time for a given trip
   */
  @Override
  public void handle(GtfsStopTime gtfsStopTime) {

    /* PREP */
    GtfsTrip gtfsTrip = data.getGtfsTripByGtfsTripId(gtfsStopTime.getTripId());
    if(gtfsTrip == null){
      LOGGER.severe(String.format("Unable to find GTFS trip %s for current GTFS stop time (stop id: %s), GTFS stop time ignored", gtfsStopTime.getTripId(), gtfsStopTime.getStopId()));
      return;
    }

    var planitRoutedService = data.getRoutedServiceByExternalId(gtfsTrip.getRouteId());
    if(planitRoutedService == null){
      LOGGER.severe(String.format("Unable to find GTFS route %s in PLANit memory model corresponding to GTFS trip %s, GTFS stop time (stop id %s) ignored", gtfsTrip.getRouteId(), gtfsTrip.getTripId(), gtfsStopTime.getStopId()));
      return;
    }
    var layer = data.getServiceNetwork().getLayerByMode(planitRoutedService.getMode());

    /* change of GTFS trip between stop times, assume current stop time is the very first stop time for the new trip */
    if(gtfsTrip != prevStopTimeTrip){
      prevSameTripStopTime = null;
    }

    /* SCHEDULED TRIP */
    RoutedTripSchedule planitTrip = collectScheduledTrip(gtfsTrip, planitRoutedService);

    /* STOP ID */
    //TODO --> continue here next time, possibly we can now match to the network if when parsing stops we simply store the GTFS stop because at this point
    //         we would have access to the mode!

    /* STOP_TIME - Arrival time/departure time */
    ExtendedLocalTime arrivalTime = GtfsUtils.parseGtfsTime(gtfsStopTime.getArrivalTime());
    ExtendedLocalTime departureTime = GtfsUtils.parseGtfsTime(gtfsStopTime.getDepartureTime());

    /* STOP_TIME - INITIAL DEPARTURE */
    if(planitTrip.getDepartures().isEmpty()){
      registerDeparture(planitTrip, gtfsStopTime, departureTime);
      registerServiceNode(layer, gtfsStopTime);
    }

    /* STOP_TIME - INTERMEDIATE STOP */
    else{
      if(prevStopTimeTrip == null){
        LOGGER.severe(String.format("GTFS trip's stop times not consecutive for GTFS trip %s, GTFS parser does not yet support such stop_time files, log GitHub feature request!",gtfsStopTime.getTripId()));
        return;
      }

      /* TIMING BETWEEN STOP and PREV STOP + SERVICE NETWORK UPDATE IF NEEDED */
      var serviceNetworkSegment = collectOrRegisterNetworkServiceSegment(layer, gtfsStopTime);
      var duration = arrivalTime.minus(GtfsUtils.parseGtfsTime(prevSameTripStopTime.getDepartureTime()));
      var dwellTime = departureTime.minus(arrivalTime);
      if(duration.exceedsSingleDay() || dwellTime.exceedsSingleDay()){
        LOGGER.severe(String.format("Duration between stops (%s) and/or dwell time at stop (%s) should be less than a day, ignored", duration, dwellTime));
        return;
      }
      planitTrip.addRelativeLegSegmentTiming(serviceNetworkSegment, duration.asLocalTimeBeforeMidnight(), dwellTime.asLocalTimeBeforeMidnight());
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
  }

}
