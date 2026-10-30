package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityType;
import org.goplanit.utils.misc.LoggingUtils;

import java.util.logging.Logger;

/**
 * Track statistics on integrating the parsed GTFS services with the physical network, where each service leg segment
 * is matched to a path on that network.
 *
 * @author markr
 */
public class GtfsIntegrationProfiler {

  /** The logger for this class */
  private static final Logger LOGGER = Logger.getLogger(GtfsIntegrationProfiler.class.getCanonicalName());

  /**
   * Tracks what became of the PLANit entities integration set out to build, holding both the totals reported here and
   * the reasons the remainder could not be built.
   * <p>
   * Integration consumes GTFS entities that have already been read and accounted for, and produces PLANit ones, so
   * what it has to report is entirely about the latter
   * </p>
   */
  private GtfsPlanitEntityDiagnostics planitEntityDiagnostics;

  /**
   * Constructor retaining and listing the default number of entity ids per issue
   */
  public GtfsIntegrationProfiler() {
    this(GtfsPlanitEntityDiagnostics.create());
  }

  /**
   * Constructor creating the diagnostics it records into, sized as given so that what is retained is settled before
   * any entity reaches them
   *
   * @param maxRetainedPerIssue upper bound on entity ids retained per issue for reporting
   * @param logSampleSizeOfRetained how many of the retained entity ids each reported issue lists
   */
  public GtfsIntegrationProfiler(final int maxRetainedPerIssue, final int logSampleSizeOfRetained) {
    this(GtfsPlanitEntityDiagnostics.create(maxRetainedPerIssue, logSampleSizeOfRetained));
  }

  /**
   * Constructor
   *
   * @param planitEntityDiagnostics to record PLANit entity outcomes into
   */
  public GtfsIntegrationProfiler(final GtfsPlanitEntityDiagnostics planitEntityDiagnostics) {
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
   * Register the outcome of a batch of service leg segments a physical path was searched for
   *
   * @param processed number of leg segments searched for
   * @param mapped number of those a path was found for
   */
  public void registerProcessedLegSegments(long processed, long mapped) {
    planitEntityDiagnostics.registerDesired(GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, processed);
    planitEntityDiagnostics.registerCreated(GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, mapped);
  }

  /**
   * Number of service leg segments a physical path was searched for so far
   *
   * @return processed leg segments
   */
  public long getProcessedLegSegments() {
    return planitEntityDiagnostics.getDesired(GtfsPlanitEntityType.SERVICE_LEG_SEGMENT);
  }

  /**
   * Log how far the search has progressed as a single line, suitable for emitting repeatedly while results are still
   * coming in.
   * <p>
   * How far along the search is, is what the line is for, so it is stated against the work there is rather than
   * against the work done so far, which would put every line at its own completion
   * </p>
   *
   * @param totalLegSegments a path is to be searched for, the whole of the work
   */
  public void logProgress(final long totalLegSegments) {
    LOGGER.info(String.format("Service leg segments searched: %s, of which mapped to network: %d",
        LoggingUtils.countWithPercentage(
            planitEntityDiagnostics.getDesired(GtfsPlanitEntityType.SERVICE_LEG_SEGMENT), totalLegSegments),
        planitEntityDiagnostics.getCreated(GtfsPlanitEntityType.SERVICE_LEG_SEGMENT)));
  }

  /**
   * reset the profiler, replacing rather than clearing what the diagnostics recorded so that anyone holding them
   * keeps what was collected so far
   */
  public void reset() {
    this.planitEntityDiagnostics = planitEntityDiagnostics.newEmptyInstance();
  }
}
