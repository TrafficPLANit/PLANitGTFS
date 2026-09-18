package org.goplanit.gtfs.converter.diagnostics;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Emits what became of a GTFS feed once everything that will be parsed from it has been.
 * <p>
 * A parse collects its account across several stages, and any one of them read on its own says little. The report is
 * therefore emitted once, by whoever ran the last stage, which is the reader itself when it is used on its own and the
 * wrapping reader when it drives several
 * </p>
 *
 * @author markr
 */
public final class GtfsCoverageReport {

  /** The logger for this class */
  private static final Logger LOGGER = Logger.getLogger(GtfsCoverageReport.class.getCanonicalName());

  /**
   * Hide constructor, this class offers no state of its own
   */
  private GtfsCoverageReport() {
  }

  /**
   * Report what became of the GTFS entities read and, where they were derived, of the PLANit entities that came from
   * them, both to the log and, when asked, to file so that the occurrences behind the totals outlive the run
   *
   * @param rawGtfsEntityDiagnostics what became of the GTFS entities
   * @param planitEntityDiagnostics what became of the PLANit entities derived from them, null when the parse derived
   *                                none
   * @param persist whether to write the per entity detail behind the totals to disk
   * @param outputDirectory to write that detail to, only used when persisting
   */
  public static void report(
      final GtfsParseDiagnostics rawGtfsEntityDiagnostics,
      final GtfsPlanitEntityDiagnostics planitEntityDiagnostics,
      final boolean persist,
      final String outputDirectory) {

    LOGGER.info("Coverage:");
    rawGtfsEntityDiagnostics.logSummary();
    if(planitEntityDiagnostics != null) {
      planitEntityDiagnostics.logSummary();
    }

    if(!persist) {
      return;
    }

    var directory = Path.of(outputDirectory);
    rawGtfsEntityDiagnostics.persist(directory);
    if(planitEntityDiagnostics != null) {
      planitEntityDiagnostics.persist(directory);
    }
    LOGGER.info(String.format("Persisted parse diagnostics to %s", directory.toAbsolutePath()));
  }
}
