package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.utils.csv.SimpleCsvWriter;
import org.goplanit.utils.misc.LogCollator;
import org.goplanit.utils.misc.LoggingUtils;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
   * How often each issue arose within each subdivision of it.
   * <p>
   * Counted here rather than read back off the retained occurrences because those are capped for reporting, so on a
   * sizeable feed they are a sample and the split taken from them would be one too
   * </p>
   */
  private final Map<GtfsPlanitEntityIssue, Map<String, LongAdder>> occurrencesBySubType = new ConcurrentHashMap<>();

  /**
   * What each subdivision of an issue says about the parser.
   * <p>
   * Held per subdivision rather than per occurrence because the two agree: a subdivision names where the entities in
   * it stood, and that is what decides the disposition, so every occurrence within one carries the same
   * </p>
   */
  private final Map<GtfsPlanitEntityIssue, Map<String, GtfsIssueDisposition>> dispositionBySubType =
      new ConcurrentHashMap<>();

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
        issue.getEntityType(), occurrence.getEntitySubType(), issue.name(),
        getDispositionOf(issue, occurrence.getEntitySubType()),
        occurrence.getEntityId(), occurrence.getExpandedDetail());
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
    registerIssueOccurrence(issue, entityId, null, detailArgs);
  }

  /**
   * Register a single occurrence of an issue that arose within a subdivision of its entity type.
   * <p>
   * A PLANit entity type is not subdivided the way a GTFS one is, having no field in the feed that classifies it.
   * What does subdivide its failures is what became of the feed entities it was to be built from, which is a property
   * of the occurrence rather than of the entity, and so is supplied per occurrence here
   * </p>
   *
   * @param issue encountered
   * @param entityId identifying the GTFS entities the entity was to be built from
   * @param subType the occurrence arose within, may be null when it could not be established
   * @param detailArgs the arguments the issue's detail template expects
   */
  public void registerIssueWithSubType(
      final GtfsPlanitEntityIssue issue, final String entityId, final String subType, final Object... detailArgs) {
    registerIssueWithSubType(issue, entityId, subType, issue.getDisposition(), detailArgs);
  }

  /**
   * Register a single occurrence of an issue that arose within a subdivision of its entity type, carrying the
   * disposition that subdivision earns rather than the one its issue is declared with.
   * <p>
   * An issue is declared with the disposition that fits an entity the run meant to keep. Where the entities behind
   * an occurrence were not such entities, the caller knows it and says so here, so a count is not read as a backlog
   * when what it holds was never wanted
   * </p>
   *
   * @param issue encountered
   * @param entityId identifying the GTFS entities the entity was to be built from
   * @param subType the occurrence arose within, may be null when it could not be established
   * @param disposition the occurrence carries
   * @param detailArgs the arguments the issue's detail template expects
   */
  public void registerIssueWithSubType(
      final GtfsPlanitEntityIssue issue, final String entityId, final String subType,
      final GtfsIssueDisposition disposition, final Object... detailArgs) {
    registerIssueOccurrence(issue, entityId, subType, detailArgs);
    if (subType != null) {
      collectCounter(
          occurrencesBySubType.computeIfAbsent(issue, absentIssue -> new ConcurrentHashMap<>()), subType).increment();
      dispositionBySubType.computeIfAbsent(issue, absentIssue -> new ConcurrentHashMap<>()).put(subType, disposition);
    }
  }

  /**
   * Collect what a subdivision of an issue says about the parser, being what its issue is declared with where the
   * subdivision earned nothing of its own
   *
   * @param issue to collect for
   * @param subType to collect for
   * @return disposition
   */
  public GtfsIssueDisposition getDispositionOf(final GtfsPlanitEntityIssue issue, final String subType) {
    var dispositions = dispositionBySubType.get(issue);
    var disposition = dispositions != null ? dispositions.get(subType) : null;
    return disposition != null ? disposition : issue.getDisposition();
  }

  /**
   * Collect how often an issue arose under each disposition its occurrences earned
   *
   * @param issue to collect for
   * @return occurrences by disposition, in the order the dispositions are declared
   */
  public SortedMap<GtfsIssueDisposition, Long> getOccurrencesByDisposition(final GtfsPlanitEntityIssue issue) {
    var byDisposition = new TreeMap<GtfsIssueDisposition, Long>();
    var occurrences = getOccurrencesBySubType(issue);
    if (occurrences.isEmpty()) {
      byDisposition.put(issue.getDisposition(), getOccurrences(issue));
      return byDisposition;
    }
    occurrences.forEach((subType, count) -> byDisposition.merge(
        getDispositionOf(issue, subType), count, Long::sum));
    return byDisposition;
  }

  /**
   * Collect how often the given issue arose within each subdivision of it, most frequent first
   *
   * @param issue to collect for
   * @return occurrences by subtype, empty where the issue is not subdivided
   */
  public SortedMap<String, Long> getOccurrencesBySubType(final GtfsPlanitEntityIssue issue) {
    var occurrences = new TreeMap<String, Long>();
    var subTypeCounters = occurrencesBySubType.get(issue);
    if (subTypeCounters != null) {
      subTypeCounters.forEach((subType, counter) -> occurrences.put(subType, counter.sum()));
    }
    return occurrences;
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
    other.dispositionBySubType.forEach((issue, dispositions) -> dispositionBySubType.computeIfAbsent(
        issue, absentIssue -> new ConcurrentHashMap<>()).putAll(dispositions));
    other.occurrencesBySubType.forEach((issue, subTypeCounters) -> subTypeCounters.forEach(
        (subType, counter) -> collectCounter(
            occurrencesBySubType.computeIfAbsent(issue, absentIssue -> new ConcurrentHashMap<>()), subType)
            .add(counter.sum())));
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
        logSubTypesOf(issue);
      }
    }
  }

  /**
   * Log how an issue divides over what became of the feed entities behind it, most frequent first.
   * <p>
   * This is what makes a large count readable: an issue is declared with the single disposition that fits it worst,
   * so a total alone reads as though every occurrence were that bad. The split says how many actually were
   * </p>
   *
   * @param issue to log for
   */
  private void logSubTypesOf(final GtfsPlanitEntityIssue issue) {
    var occurrencesBySubType = getOccurrencesBySubType(issue);
    if (occurrencesBySubType.size() < 2) {
      /* a single subdivision only restates the entry above it */
      return;
    }

    long total = occurrencesBySubType.values().stream().mapToLong(Long::longValue).sum();
    occurrencesBySubType.entrySet().stream()
        .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
        .forEach(entry -> LOGGER.info(LoggingUtils.settingsValue(
            describeSubType(entry.getKey()),
            LoggingUtils.countWithPercentage(entry.getValue(), total, "occurrences")
                + " [" + getDispositionOf(issue, entry.getKey()) + "]", 3)));
  }

  /**
   * Describe a subdivision as the log speaks of it.
   * <p>
   * Where the subdivision names the GTFS issue a loss originated in, it is stated exactly as that issue is stated
   * where it was first reported. The same loss seen twice reads as the same loss, rather than as an entry the reader
   * has to match to another by its constant. A listing outliving the run keeps the constant, which is what a later
   * reader can join on
   * </p>
   *
   * @param subType to describe
   * @return description, the subtype itself where it names no issue
   */
  private static String describeSubType(final String subType) {
    var originIssue = GtfsParseIssue.findByName(subType);
    return originIssue != null ? originIssue.getDescription() : subType;
  }

  /**
   * {@inheritDoc}
   * <p>
   * A derived entity is lost for reasons that differ in kind within a single issue, most of them because the feed
   * entities behind it were never wanted, so a single declared disposition would describe a fraction of the count
   * and misrepresent the rest. The split is stated instead
   * </p>
   */
  @Override
  protected String createDispositionLabel(final GtfsPlanitEntityIssue issue) {
    var byDisposition = getOccurrencesByDisposition(issue);
    if (byDisposition.size() < 2) {
      return byDisposition.isEmpty()
          ? issue.getDisposition().name() : byDisposition.firstKey().name();
    }
    return byDisposition.entrySet().stream()
        .map(entry -> String.format("%d %s", entry.getValue(), entry.getKey()))
        .collect(Collectors.joining(", "));
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
    occurrencesBySubType.clear();
    dispositionBySubType.clear();
  }
}
