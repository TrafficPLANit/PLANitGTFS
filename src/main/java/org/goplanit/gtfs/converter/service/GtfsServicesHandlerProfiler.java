package org.goplanit.gtfs.converter.service;

import org.goplanit.gtfs.enums.RouteType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Track statistics across GTFS services related handlers
 * 
 * @author markr
 *
 */
public class GtfsServicesHandlerProfiler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesHandlerProfiler.class.getCanonicalName());

  /** track how many GTFS routes were processed by route type*/
  private Map<RouteType,LongAdder> gtfsRoutesCounter;

  /** track how many GTFS schedule based trips were processed */
  private LongAdder gtfsScheduleBasedTripCounter;

  /** track how many GTFS trip stop times were processed */
  private LongAdder gtfsTripStopTimeCounter;

  /** track how many GTFS frequency entries were processed*/
  private LongAdder gtfsFrequencyCounter;

  /** Initialise the profiler */
  private void initialise(){
    gtfsRoutesCounter = new HashMap<>();
    gtfsTripStopTimeCounter = new LongAdder();
    gtfsScheduleBasedTripCounter = new LongAdder();
    gtfsFrequencyCounter = new LongAdder();
  }

  /**
   * Default constructor
   */
  public GtfsServicesHandlerProfiler() {
    initialise();
  }


  /**
   * log counters regarding main processing phase
   *
   */
  public void logProcessingStats() {
    gtfsRoutesCounter.forEach( (k,v) -> LOGGER.info(String.format("[STATS] processed %d GTFS routes - %s ",v.longValue(), k)));
    LOGGER.info(String.format("[STATS] processed %d GTFS trips (scheduled)",gtfsScheduleBasedTripCounter.longValue()));
    LOGGER.info(String.format("[STATS] processed %d GTFS trip stop times",gtfsTripStopTimeCounter.longValue()));
    LOGGER.info(String.format("[STATS] processed %d GTFS trip frequency entries",gtfsFrequencyCounter.longValue()));
  }

  /**
   * reset the profiler
   */
  public void reset() {
    gtfsRoutesCounter.clear();
    gtfsScheduleBasedTripCounter.reset();
    gtfsTripStopTimeCounter.reset();
    gtfsFrequencyCounter.reset();
  }

  /**
   * Increment count for a processed GTFS route
   *
   * @param gtfsRouteType of the route
   */
  public void incrementRouteCount(RouteType gtfsRouteType) {
    var routeTypeAdder = gtfsRoutesCounter.get(gtfsRouteType);
    if(routeTypeAdder == null){
      routeTypeAdder = new LongAdder();
      gtfsRoutesCounter.put(gtfsRouteType, routeTypeAdder);
    }
    routeTypeAdder.increment();
  }

  /**
   * Increment count for a processed GTFS frequency
   */
  public void incrementTripFrequencyCount() { gtfsFrequencyCounter.increment();}

  /**
   * Increment count for a processed GTFS trips (scheduled)
   */
  public void incrementScheduledTripCount() {
    gtfsScheduleBasedTripCounter.increment();
  }

  /**
   * Increment count for a processed GTFStrip stop times
   */
  public void incrementTripStopTimeCount() {
    gtfsTripStopTimeCounter.increment();
  }
}
