package org.goplanit.gtfs.converter.service;

import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.utils.misc.Pair;
import org.goplanit.zoning.Zoning;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Track statistics on GTFS Routes handler
 * 
 * @author markr
 *
 */
public class GtfsRoutesHandlerProfiler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(GtfsRoutesHandlerProfiler.class.getCanonicalName());

  /** track how many GTFS objects were processed, e.g., incorporated, as it should not count discarded entries */
  private LongAdder gtfsRoutesCounter;

  /** Initialise the profiler */
  private void initialise(){
    gtfsRoutesCounter = new LongAdder();
  }

  /**
   * Default constructor
   */
  public GtfsRoutesHandlerProfiler() {
    initialise();
  }


  /**
   * log counters regarding main processing phase
   *
   * @param zoning for which information  was tracked
   */
  public void logProcessingStats(Zoning zoning) {
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
