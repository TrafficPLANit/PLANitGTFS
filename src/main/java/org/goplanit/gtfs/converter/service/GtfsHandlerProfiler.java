package org.goplanit.gtfs.converter.service;

import org.goplanit.zoning.Zoning;

import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Track statistics across GTFS handlers
 * 
 * @author markr
 *
 */
public class GtfsHandlerProfiler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(GtfsHandlerProfiler.class.getCanonicalName());

  /** track how many GTFS routes were processed */
  private LongAdder gtfsRoutesCounter;

  /** track how many GTFS schedule based trips were processed */
  private LongAdder gtfsScheduleBasedTripCounter;

  /** track how many GTFS trip stop times were processed */
  private LongAdder gtfsTripStopTimeCounter;

  /** track how many GTFS frequency entries were processed*/
  private LongAdder gtfsFrequencyCounter;

  /** Initialise the profiler */
  private void initialise(){
    gtfsRoutesCounter = new LongAdder();
  }

  /**
   * Default constructor
   */
  public GtfsHandlerProfiler() {
    initialise();
  }


  /**
   * log counters regarding main processing phase
   *
   */
  public void logProcessingStats() {
    LOGGER.info(String.format("[STATS] processed %d GTFS routes",gtfsRoutesCounter.longValue()));
    LOGGER.info(String.format("[STATS] processed %d GTFS trips (scheduled)",gtfsScheduleBasedTripCounter.longValue()));
    LOGGER.info(String.format("[STATS] processed %d GTFS trip stop times",gtfsTripStopTimeCounter.longValue()));
    LOGGER.info(String.format("[STATS] processed %d GTFS trip frequency entries",gtfsFrequencyCounter.longValue()));
  }

  /**
   * reset the profiler
   */
  public void reset() {
    gtfsRoutesCounter.reset();
    gtfsScheduleBasedTripCounter.reset();
    gtfsTripStopTimeCounter.reset();
    gtfsFrequencyCounter.reset();
  }

  /**
   * Increment count for a processed GTFS route
   */
  public void incrementRouteCount() {
    gtfsRoutesCounter.increment();
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
