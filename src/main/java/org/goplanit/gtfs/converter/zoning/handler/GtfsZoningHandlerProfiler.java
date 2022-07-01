package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.zoning.Zoning;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Track statistics on GTFS zoning handler
 * 
 * @author markr
 *
 */
public class GtfsZoningHandlerProfiler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningHandlerProfiler.class.getCanonicalName());


  /**
   * Default constructor
   */
  public GtfsZoningHandlerProfiler() {
  }


  /**
   * log counters regarding main processing phase
   *
   * @param zoning for which information  was tracked
   */
  public void logProcessingStats(Zoning zoning) {

    //TODO: something like the below only for GTFS
    //LOGGER.info(String.format("[STATS] created PLANit %d transfer zone groups",zoning.getTransferZoneGroups().size()));

  }

  /**
   * reset the profiler
   */
  public void reset() {
    //TODO
  }
 

}
