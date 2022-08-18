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

  /** track how many GTFS objects were processed, e.g., incorporated, as it should not count discarded entries */
  private LongAdder gtfsRoutesCounter;

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
    LOGGER.info(String.format("[STATS] converted %d GTFS routes in PLANit routes",gtfsRoutesCounter.longValue()));
  }

  /**
   * reset the profiler
   */
  public void reset() {
    gtfsRoutesCounter.reset();
  }

  /**
   * Increment count for a processed GTFS route
   */
  public void incrementRouteCount() {
    gtfsRoutesCounter.increment();
  }

}
