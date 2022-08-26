package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.utils.arrays.ArrayUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.zoning.Zoning;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

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

  /** track how many GTFS objects were processed, e.g., incoporated, as it should not count discarded entries */
  Map<GtfsObjectType, LongAdder> gtfsObjectTypeCounters = new HashMap<>();

  /** track number of newly created transfer zones and augmented existing transfer zones */
  Pair<LongAdder, LongAdder> transferZoneCounterPair;

  /** Initialise the profiler */
  private void initialise(){
    Arrays.stream(GtfsObjectType.values()).forEach( type -> gtfsObjectTypeCounters.put(type, new LongAdder()));
    transferZoneCounterPair = Pair.of(new LongAdder(), new LongAdder());
  }

  /**
   * Default constructor
   */
  public GtfsZoningHandlerProfiler() {
    initialise();
  }


  /**
   * log counters regarding main processing phase
   *
   * @param zoning for which information  was tracked
   */
  public void logProcessingStats(Zoning zoning) {
    /* log for each GTFS object type */
    gtfsObjectTypeCounters.forEach( (k,v) -> {
      if(v.longValue() > 0 ){
        LOGGER.info(String.format("[STATS] converted %d GTFS %s entities in PLANit entities",v.longValue(), k.toString()));
      }
    });

    /* GTFS -> transfer zones */
    zoning.logInfo(LoggingUtils.zoningPrefix(zoning.getId()).concat("[STATS]"));
  }

  /**
   * reset the profiler
   */
  public void reset() {
    gtfsObjectTypeCounters.values().forEach( v -> v.reset());
    transferZoneCounterPair.<LongAdder>both( e -> e.reset());
  }

  /**
   * Increment count for a processed (not discarded) GTFS object type irrespective of how it was processed, i.e., it does
   * not matter if it results in a new PLANit entity or an augmented existing one
   * @param type to increment for
   */
  public void incrementCount(GtfsObjectType type) {
    gtfsObjectTypeCounters.get(type).increment();
  }

  /**
   * Increment count for a augmented existing transfer zones
   */
  public void incrementAugmentedTransferZones(){
    this.transferZoneCounterPair.second().increment();
  }

  /**
   * Increment count for a newly created transfer zones from GTFS data
   */
  public void incrementCreatedTransferZones(){
    this.transferZoneCounterPair.first().increment();
  }
}
