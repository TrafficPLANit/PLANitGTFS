package org.goplanit.gtfs.converter.service;

import org.goplanit.gtfs.converter.diagnostics.GtfsParseDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseIssue;
import org.goplanit.gtfs.converter.diagnostics.GtfsScopeState;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.enums.RouteType;

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

  /**
   * Tracks what became of each GTFS entity encountered, holding both the totals reported here and the outcome of
   * every entity that did not survive. When a composite reader drives several stages, the same instance is handed to
   * each so the stages report as a single funnel
   */
  private GtfsParseDiagnostics diagnostics;

  /**
   * Constructor using its own diagnostics, for when the services reader runs standalone
   */
  public GtfsServicesHandlerProfiler() {
    this(GtfsParseDiagnostics.create());
  }

  /**
   * Constructor
   *
   * @param diagnostics to record into
   */
  public GtfsServicesHandlerProfiler(final GtfsParseDiagnostics diagnostics) {
    this.diagnostics = diagnostics;
  }

  /**
   * Collect the diagnostics being recorded into
   *
   * @return diagnostics
   */
  public GtfsParseDiagnostics getDiagnostics() {
    return diagnostics;
  }

  /**
   * reset the profiler, replacing rather than clearing what was recorded so that anyone holding the diagnostics
   * collected so far keeps them
   */
  public void reset() {
    this.diagnostics = diagnostics.newEmptyInstance();
  }

  /**
   * Register a GTFS route encountered in the feed, irrespective of what becomes of it
   *
   * @param gtfsRouteType of the route
   * @param gtfsRouteId of the route
   */
  public void registerSeenRoute(RouteType gtfsRouteType, String gtfsRouteId) {
    diagnostics.registerSeen(GtfsObjectType.ROUTE, gtfsRouteType, gtfsRouteId);
  }

  /**
   * Increment count for a processed GTFS frequency
   */
  public void incrementTripFrequencyCount() {
    diagnostics.registerSeen(GtfsObjectType.FREQUENCY);
  }

  /**
   * Register a GTFS trip encountered in the feed, irrespective of what becomes of it
   */
  public void registerSeenTrip() {
    diagnostics.registerSeen(GtfsObjectType.TRIP);
  }

  /**
   * Register a GTFS stop time encountered in the feed, standing as given
   *
   * @param state the stop time stands in, which a stop time is counted against rather than indexed under, a feed
   *          holding millions of them
   */
  public void registerSeenStopTime(final GtfsScopeState state) {
    diagnostics.registerSeen(GtfsObjectType.STOP_TIME, null, state);
  }
}
