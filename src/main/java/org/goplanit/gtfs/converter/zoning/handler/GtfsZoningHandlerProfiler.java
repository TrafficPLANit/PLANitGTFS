package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.diagnostics.GtfsParseDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityIssue;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityType;
import org.goplanit.gtfs.converter.diagnostics.GtfsZoningEntityOrigin;

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
   * Tracks what became of each GTFS entity encountered, holding both the totals reported here and the outcome of
   * every entity that did not survive
   */
  private GtfsParseDiagnostics diagnostics;

  /** Tracks the PLANit entities the stop stage derives from the feed, being the zones stops are boarded from and the
   * access points granting those zones entry to the physical network */
  private GtfsPlanitEntityDiagnostics planitEntityDiagnostics;

  /**
   * Constructor using its own diagnostics, for when the zoning reader runs standalone
   */
  public GtfsZoningHandlerProfiler() {
    this(GtfsParseDiagnostics.create());
  }

  /**
   * Constructor
   *
   * @param diagnostics to record into
   */
  public GtfsZoningHandlerProfiler(final GtfsParseDiagnostics diagnostics) {
    this(diagnostics, GtfsPlanitEntityDiagnostics.create());
  }

  /**
   * Constructor
   *
   * @param diagnostics to record what became of each GTFS entity into
   * @param planitEntityDiagnostics to record the PLANit entities derived from the feed into
   */
  public GtfsZoningHandlerProfiler(
      final GtfsParseDiagnostics diagnostics, final GtfsPlanitEntityDiagnostics planitEntityDiagnostics) {
    this.diagnostics = diagnostics;
    this.planitEntityDiagnostics = planitEntityDiagnostics;
  }

  /**
   * Collect the PLANit entity diagnostics being recorded into
   *
   * @return PLANit entity diagnostics
   */
  public GtfsPlanitEntityDiagnostics getPlanitEntityDiagnostics() {
    return planitEntityDiagnostics;
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
   * reset the profiler, replacing rather than clearing what the diagnostics recorded so that anyone holding them
   * keeps what was collected so far
   */
  public void reset() {
    this.diagnostics = diagnostics.newEmptyInstance();
    this.planitEntityDiagnostics = planitEntityDiagnostics.newEmptyInstance();
  }

  /**
   * Register a GTFS stop attached to a pre-existing transfer zone
   *
   * @param origin the rule that identified the pre-existing zone as the stop's
   */
  public void registerMatchedTransferZone(final GtfsZoningEntityOrigin origin){
    planitEntityDiagnostics.registerCreated(
        GtfsPlanitEntityType.STOP_TRANSFER_ZONE_MAPPING, origin.getSubType(), 1);
  }

  /**
   * Register a stop attached to a pre-existing transfer zone the settings pinned it to, no rule having been applied
   */
  public void registerTransferZoneMappedBySettings(){
    planitEntityDiagnostics.registerCreated(
        GtfsPlanitEntityType.STOP_TRANSFER_ZONE_MAPPING,
        GtfsZoningEntityOrigin.MAPPED_BY_SETTINGS.getSubType(), 1);
  }

  /**
   * Register a transfer zone newly created from GTFS data
   */
  public void registerCreatedTransferZone(){
    planitEntityDiagnostics.registerCreated(
        GtfsPlanitEntityType.STOP_TRANSFER_ZONE_MAPPING,
        GtfsZoningEntityOrigin.CREATED_FOR_STOP.getSubType(), 1);
  }

}
