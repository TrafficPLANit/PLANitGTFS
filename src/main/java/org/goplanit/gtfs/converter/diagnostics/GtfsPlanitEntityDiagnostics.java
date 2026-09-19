package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.utils.csv.SimpleCsvWriter;
import org.goplanit.utils.misc.LogCollator;
import org.goplanit.utils.misc.LoggingUtils;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * What became of the PLANit entities the converter derives from the feed.
 * <p>
 * A derived entity has no feed side denominator, so it is not scored as a share of the feed. What can be stated is how
 * many of each kind were meant to exist and how many are in the result, with the shortfall attributed to the issues
 * that caused it. Both ways of falling short are counted the same way: an entity that could never be built never
 * reaches the result, and neither does one that was built and subsequently removed
 * </p>
 *
 * @author markr
 */
public class GtfsPlanitEntityDiagnostics extends GtfsDiagnosticsBase<GtfsPlanitEntityIssue> {

  /** The logger for this class */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitEntityDiagnostics.class.getCanonicalName());

  /** entities of each type the converter set out to build */
  private final Map<GtfsPlanitEntityType, LongAdder> desiredByType = new ConcurrentHashMap<>();

  /** entities of each type that came about */
  private final Map<GtfsPlanitEntityType, LongAdder> createdByType = new ConcurrentHashMap<>();

  /** occurrences of every issue recorded */
  private final LogCollator issues;

  /**
   * Constructor
   *
   * @param maxRetainedPerIssue upper bound on entity ids retained per issue for reporting
   * @param logSampleSizeOfRetained how many of the retained entity ids each reported issue lists
   */
  protected GtfsPlanitEntityDiagnostics(final int maxRetainedPerIssue, final int logSampleSizeOfRetained) {
    super(maxRetainedPerIssue, logSampleSizeOfRetained);
    this.issues = LogCollator.createWithRetentionLimit(maxRetainedPerIssue);
    applyLogSampleSizeOfRetained();
  }

  /**
   * Factory method retaining and listing the default number of entity ids per issue
   *
   * @return created diagnostics
   */
  public static GtfsPlanitEntityDiagnostics create() {
    return new GtfsPlanitEntityDiagnostics(
        DEFAULT_MAX_RETAINED_PER_ISSUE, LogCollator.DEFAULT_LOG_SAMPLE_SIZE_OF_RETAINED);
  }

  /**
   * Factory method for diagnostics retaining at most the given number of entity ids per issue, of which the given
   * number are listed whenever the issue is reported
   *
   * @param maxRetainedPerIssue upper bound on entity ids retained per issue for reporting
   * @param logSampleSizeOfRetained how many of the retained entity ids each reported issue lists
   * @return created diagnostics
   */
  public static GtfsPlanitEntityDiagnostics create(
      final int maxRetainedPerIssue, final int logSampleSizeOfRetained) {
    return new GtfsPlanitEntityDiagnostics(maxRetainedPerIssue, logSampleSizeOfRetained);
  }

  /**
   * Factory method for diagnostics keeping totals only, i.e. retaining no entity ids at all
   *
   * @return created diagnostics
   */
  public static GtfsPlanitEntityDiagnostics createCountsOnly() {
    return new GtfsPlanitEntityDiagnostics(LogCollator.NO_RETENTION, LogCollator.NO_RETENTION);
  }

  /**
   * Create an empty instance configured as this one is, leaving what this instance collected intact for anyone
   * holding on to it
   *
   * @return created diagnostics
   */
  public GtfsPlanitEntityDiagnostics newEmptyInstance() {
    return new GtfsPlanitEntityDiagnostics(getMaxRetainedPerIssue(), getLogSampleSizeOfRetained());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected LogCollator collatorFor(final GtfsPlanitEntityIssue issue) {
    return issues;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Collection<LogCollator> getCollators() {
    return List.of(issues);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected GtfsPlanitEntityIssue issueValueOf(final String name) {
    return GtfsPlanitEntityIssue.valueOf(name);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected long getDenominator(final GtfsPlanitEntityIssue issue) {
    return getDesired(issue.getEntityType());
  }

  /**
   * {@inheritDoc}
   * <p>
   * The outcome plays no part here, an entity that was never built having reached none
   * </p>
   */
  @Override
  protected void writeIssueRow(
      final SimpleCsvWriter csvWriter, final GtfsPlanitEntityIssue issue, final LogCollator.Occurrence occurrence,
      final GtfsParseOutcome outcome) {
    csvWriter.writeRow(
        issue.getEntityType(), issue.name(), issue.getDisposition(), occurrence.getEntityId(),
        occurrence.getExpandedDetail());
  }

  /**
   * Register entities of a type that were to be in the result, i.e. those the converter set out to build, or those it
   * had already built when a post-processing step that may remove them commenced
   *
   * @param entityType concerned
   * @param numberOfEntities that were to be in the result
   */
  public void registerDesired(final GtfsPlanitEntityType entityType, final long numberOfEntities) {
    collectCounter(desiredByType, entityType).add(numberOfEntities);
  }

  /**
   * Register entities of a type that are in the result, i.e. those that came about and were not removed again
   *
   * @param entityType concerned
   * @param numberOfEntities in the result
   */
  public void registerCreated(final GtfsPlanitEntityType entityType, final long numberOfEntities) {
    collectCounter(createdByType, entityType).add(numberOfEntities);
  }

  /**
   * Register a single occurrence of an issue
   *
   * @param issue encountered
   * @param entityId identifying the GTFS entities the entity was to be built from
   * @param detailArgs the arguments the issue's detail template expects
   */
  public void registerIssue(
      final GtfsPlanitEntityIssue issue, final String entityId, final Object... detailArgs) {
    /* PLANit entity types are not subdivided, so no subtype accompanies their occurrences */
    registerIssueOccurrence(issue, entityId, null, detailArgs);
  }

  /**
   * Collect how many entities of a type the converter set out to build
   *
   * @param entityType to collect for
   * @return number set out to build
   */
  public long getDesired(final GtfsPlanitEntityType entityType) {
    var counter = desiredByType.get(entityType);
    return counter != null ? counter.sum() : 0;
  }

  /**
   * Collect how many entities of a type came about
   *
   * @param entityType to collect for
   * @return number created
   */
  public long getCreated(final GtfsPlanitEntityType entityType) {
    var counter = createdByType.get(entityType);
    return counter != null ? counter.sum() : 0;
  }

  /**
   * Absorb what the given diagnostics recorded, so the stages that contributed to a parse can be reported as one
   *
   * @param other to absorb
   */
  public void merge(final GtfsPlanitEntityDiagnostics other) {
    other.desiredByType.forEach((type, counter) -> collectCounter(desiredByType, type).add(counter.sum()));
    other.createdByType.forEach((type, counter) -> collectCounter(createdByType, type).add(counter.sum()));
    issues.merge(other.issues);
  }

  /**
   * Log what became of the PLANit entities the converter set out to build
   */
  public void logSummary() {
    LOGGER.info(LoggingUtils.surroundWithBrackets("PLANIT ENTITIES") + "derived from the feed");

    for (var entityType : GtfsPlanitEntityType.values()) {
      if (getDesired(entityType) == 0) {
        continue;
      }
      LOGGER.info(LoggingUtils.settingsValue(
          String.format("%s in result", entityType.getLabel()),
          LoggingUtils.countWithPercentage(getCreated(entityType), getDesired(entityType)), 2));
    }

    for (var issue : GtfsPlanitEntityIssue.values()) {
      if (isReportedInSummary(issue)) {
        LOGGER.info(createIssueLogEntry(issue));
      }
    }
  }

  /**
   * Persist one row per recorded occurrence
   *
   * @param outputDirectory to write to
   */
  public void persist(final Path outputDirectory) {
    persistEntityIssues(
        outputDirectory.resolve("gtfs_planit_entity_issues.csv"), issues, GtfsPlanitEntityCsvColumn.getHeaders(),
        null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    desiredByType.clear();
    createdByType.clear();
  }
}
