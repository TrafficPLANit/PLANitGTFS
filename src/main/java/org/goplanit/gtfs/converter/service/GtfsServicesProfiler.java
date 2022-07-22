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
 * Track statistics on GTFS services reader through underlying profilers for various GFS file handlers
 * 
 * @author markr
 *
 */
public class GtfsServicesProfiler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesProfiler.class.getCanonicalName());

  private GtfsRoutesHandlerProfiler gtfsRouteProfiler;

  /** Initialise the profiler */
  private void initialise(){
    gtfsRouteProfiler = new GtfsRoutesHandlerProfiler();
  }

  /**
   * Default constructor
   */
  public GtfsServicesProfiler() {
    initialise();
  }


  /**
   * log profile information that was gathered
   *
   */
  public void logProcessingStats() {
    //todo
  }

  /**
   * reset the profiler
   */
  public void reset() {
    gtfsRouteProfiler.reset();
  }

  public GtfsRoutesHandlerProfiler getGtfsRoutesProfiler(){
    return gtfsRouteProfiler;
  }

}
