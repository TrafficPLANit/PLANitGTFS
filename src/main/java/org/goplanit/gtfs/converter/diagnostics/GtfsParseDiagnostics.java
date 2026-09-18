package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.utils.csv.SimpleCsvWriter;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.LogCollator;
import org.goplanit.utils.misc.LoggingUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ToLongFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Records what became of every GTFS entity the converter encountered, across all three stages.
 * <p>
 * The parser counts what it processes; what it drops is what determines whether a feed was actually landed. This
 * tracks both, so an entity that was seen but never reached the memory model is accounted for by name and reason
 * rather than disappearing between two totals that do not reconcile.
 * </p>
 * <p>
 * Counts are kept on two axes. Every entity is counted against its type, and where a call site supplies one, also
 * against a category within that type such as the route type a route belongs to. The same entity therefore appears in
 * aggregate and broken down, without either being derived from the other.
 * </p>
 * <p>
 * It doubles as the suppression index the converter needs anyway: once a route or trip is registered as discarded,
 * later stages can ask whether an entity referencing it should be processed at all, rather than each stage keeping its
 * own record of the same fact.
 * </p>
 * <p>
 * Safe for concurrent use, which the integration stage requires since it maps leg segments from worker threads.
 * </p>
 *
 * @author markr
 */
public class GtfsParseDiagnostics extends GtfsDiagnosticsBase<GtfsParseIssue> {

  /** The logger for this class */
  private static final Logger LOGGER = Logger.getLogger(GtfsParseDiagnostics.class.getCanonicalName());

  /**
   * Entity types whose discards are indexed by id so that later stages can test membership.
   * <p>
   * Deliberately excludes stop times: a feed the size of Sydney's holds millions of them, and retaining an id per
   * discard would trade a lookup for a memory problem. Their discards are counted, not indexed.
   * </p>
   */
  private static final Set<GtfsObjectType> INDEXED_ENTITY_TYPES =
      Set.of(GtfsObjectType.ROUTE, GtfsObjectType.TRIP);

  /** how many GTFS entities of each type were encountered */
  private final Map<GtfsObjectType, LongAdder> seenByEntityType = new ConcurrentHashMap<>();

  /** how many GTFS entities of each type were encountered per category, where a category was supplied */
  private final Map<GtfsObjectType, Map<String, LongAdder>> seenByEntityTypeAndCategory = new ConcurrentHashMap<>();

  /** how many GTFS entities of each type were encountered per scope, where a scope could be established */
  private final Map<GtfsObjectType, Map<GtfsEntityScope, LongAdder>> seenByEntityTypeAndScope =
      new ConcurrentHashMap<>();

  /** how often each issue was registered per category, where a category was supplied */
  private final Map<GtfsParseIssue, Map<String, LongAdder>> issuesByCategory = new ConcurrentHashMap<>();

  /** issues that cost the entity, keyed by issue name */
  private final LogCollator discards;

  /** issues carried by an entity that was parsed regardless, keyed by issue name */
  private final LogCollator retainedIssues;

  /** discarded entity ids and the issue responsible, for the indexed entity types only */
  private final Map<GtfsObjectType, Map<String, GtfsParseIssue>> discardedEntityIndex =
      new EnumMap<>(GtfsObjectType.class);

  /**
   * Constructor
   *
   * @param maxRetainedPerIssue upper bound on entity ids retained per issue for reporting
   * @param logSampleSizeOfRetained how many of the retained entity ids each reported issue lists
   */
  protected GtfsParseDiagnostics(final int maxRetainedPerIssue, final int logSampleSizeOfRetained) {
    super(maxRetainedPerIssue, logSampleSizeOfRetained);
    this.discards = LogCollator.createWithRetentionLimit(maxRetainedPerIssue);
    this.retainedIssues = LogCollator.createWithRetentionLimit(maxRetainedPerIssue);
    INDEXED_ENTITY_TYPES.forEach(type -> discardedEntityIndex.put(type, new ConcurrentHashMap<>()));
    applyLogSampleSizeOfRetained();
  }

  /**
   * Create diagnostics retaining and listing the default number of entity ids per issue
   *
   * @return created diagnostics
   */
  public static GtfsParseDiagnostics create() {
    return new GtfsParseDiagnostics(
        DEFAULT_MAX_RETAINED_PER_ISSUE, LogCollator.DEFAULT_LOG_SAMPLE_SIZE_OF_RETAINED);
  }

  /**
   * Create diagnostics retaining at most the given number of entity ids per issue, of which the given number are
   * listed whenever the issue is reported
   *
   * @param maxRetainedPerIssue upper bound on entity ids retained per issue
   * @param logSampleSizeOfRetained how many of the retained entity ids each reported issue lists
   * @return created diagnostics
   */
  public static GtfsParseDiagnostics create(final int maxRetainedPerIssue, final int logSampleSizeOfRetained) {
    return new GtfsParseDiagnostics(maxRetainedPerIssue, logSampleSizeOfRetained);
  }

  /**
   * Create diagnostics that count without retaining any entity ids, for a run wanting the totals but not the per
   * entity listings. The suppression index is unaffected, since later stages depend on it
   *
   * @return created diagnostics
   */
  public static GtfsParseDiagnostics createCountsOnly() {
    return new GtfsParseDiagnostics(LogCollator.NO_RETENTION, LogCollator.NO_RETENTION);
  }

  /**
   * Create an empty instance configured as this one is, leaving what this instance collected intact for anyone
   * holding a reference to it
   *
   * @return created diagnostics
   */
  public GtfsParseDiagnostics newEmptyInstance() {
    return new GtfsParseDiagnostics(getMaxRetainedPerIssue(), getLogSampleSizeOfRetained());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected LogCollator collatorFor(final GtfsParseIssue issue) {
    return issue.isDiscarding() ? discards : retainedIssues;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Collection<LogCollator> getCollators() {
    return List.of(discards, retainedIssues);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected GtfsParseIssue issueValueOf(final String name) {
    return GtfsParseIssue.valueOf(name);
  }

  /**
   * {@inheritDoc}
   */
  /**
   * {@inheritDoc}
   * <p>
   * Measured against every entity of the type the feed holds, not against those in reach of the run. An in reach
   * denominator requires the numerator to be restricted the same way, and it is not: an issue such as a stop serving
   * no parsed mode is registered before the stop is ever tested against the area covered, so out of reach entities
   * reach it. Dividing those by the in reach total yields shares well beyond 100%. The two become consistent once
   * out of scope entities are skipped before such checks are reached
   * </p>
   */
  @Override
  protected long getDenominator(final GtfsParseIssue issue) {
    return getSeen(issue.getEntityType());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void writeIssueRow(
      final SimpleCsvWriter csvWriter, final GtfsParseIssue issue, final LogCollator.Occurrence occurrence,
      final GtfsParseOutcome outcome) {
    csvWriter.writeRow(
        issue.getStage(),
        issue.getEntityType(),
        issue.name(),
        issue.getDisposition(),
        outcome.getLabel(),
        occurrence.getEntityId(),
        occurrence.getExpandedDetail());
  }

  /**
   * Register that an entity of the given type was encountered in the feed, irrespective of what became of it
   *
   * @param entityType encountered
   */
  public void registerSeen(final GtfsObjectType entityType) {
    registerSeen(entityType, 1);
  }

  /**
   * Register that a number of entities of the given type were encountered in the feed
   *
   * @param entityType encountered
   * @param count how many
   */
  public void registerSeen(final GtfsObjectType entityType, final long count) {
    collectCounter(seenByEntityType, entityType).add(count);
  }

  /**
   * Register that an entity of the given type was encountered, counted both against its type and within a category of
   * that type, e.g. a route within its route type
   *
   * @param entityType encountered
   * @param category within the entity type
   */
  public void registerSeen(final GtfsObjectType entityType, final Enum<?> category) {
    registerSeen(entityType, category, 1);
  }

  /**
   * Register that a number of entities of the given type were encountered, counted both against their type and within
   * a category of that type
   *
   * @param entityType encountered
   * @param category within the entity type
   * @param count how many
   */
  public void registerSeen(final GtfsObjectType entityType, final Enum<?> category, final long count) {
    registerSeen(entityType, count);
    if (category != null) {
      collectCounter(
          collectCategoryCounters(seenByEntityTypeAndCategory, entityType), category.name()).add(count);
    }
  }

  /**
   * Register that an entity of the given type was encountered and where it sits relative to the area the run covers.
   * <p>
   * The scope axis is kept separate from the category axis, a category describing what an entity is and a scope
   * describing where it is. An entity can therefore be counted against both without either being derived from the
   * other
   * </p>
   *
   * @param entityType encountered
   * @param scope of the entity relative to the area the run covers
   */
  public void registerSeenInScope(final GtfsObjectType entityType, final GtfsEntityScope scope) {
    registerSeenInScope(entityType, scope, 1);
  }

  /**
   * Register that a number of entities of the given type were encountered with the given scope
   *
   * @param entityType encountered
   * @param scope of the entities relative to the area the run covers
   * @param count how many
   */
  public void registerSeenInScope(
      final GtfsObjectType entityType, final GtfsEntityScope scope, final long count) {
    if (scope != null) {
      collectCounter(
          seenByEntityTypeAndScope.computeIfAbsent(entityType, t -> new ConcurrentHashMap<>()), scope).add(count);
    }
  }

  /**
   * Collect how many entities of a type were encountered with the given scope
   *
   * @param entityType to collect for
   * @param scope to collect for
   * @return number encountered
   */
  public long getSeen(final GtfsObjectType entityType, final GtfsEntityScope scope) {
    var scopeCounters = seenByEntityTypeAndScope.get(entityType);
    if (scopeCounters == null) {
      return 0;
    }
    var counter = scopeCounters.get(scope);
    return counter != null ? counter.sum() : 0;
  }

  /**
   * Collect how many entities of a type were ever in reach of the run, i.e. wholly or partly within the area it
   * covers. This is the denominator anything the parser achieved should be measured against
   *
   * @param entityType to collect for
   * @return number in reach
   */
  public long getSeenInReach(final GtfsObjectType entityType) {
    return getSeen(entityType, GtfsEntityScope.IN) + getSeen(entityType, GtfsEntityScope.PARTIAL);
  }

  /**
   * Verify whether the scope of entities of a type was established at all. Where it was not, every entity of that type
   * has to be treated as in reach, there being nothing to say otherwise
   *
   * @param entityType to verify for
   * @return true when scope was recorded, false otherwise
   */
  public boolean hasScope(final GtfsObjectType entityType) {
    var scopeCounters = seenByEntityTypeAndScope.get(entityType);
    return scopeCounters != null && !scopeCounters.isEmpty();
  }

  /**
   * Register an issue against an entity, discarding it or not according to the issue.
   * <p>
   * Kept distinct from the variable argument form so that an issue taking no context, which on a sizeable feed is the
   * form registered millions of times, costs no array to register
   * </p>
   *
   * @param issue encountered
   * @param entityId the GTFS id of the entity concerned, may be null when not entity specific
   */
  public void registerIssue(final GtfsParseIssue issue, final String entityId) {
    registerIssue(issue, null, entityId, NO_DETAIL_ARGS);
  }

  /**
   * Register an issue against an entity, supplying the arguments its detail template expects. The wording is composed
   * by the issue itself, so a site registering it need only supply the values
   *
   * @param issue encountered
   * @param entityId the GTFS id of the entity concerned, may be null when not entity specific
   * @param detailArgs the arguments the issue's detail template expects
   */
  public void registerIssue(final GtfsParseIssue issue, final String entityId, final Object... detailArgs) {
    registerIssue(issue, null, entityId, detailArgs);
  }

  /**
   * Register an issue against an entity, additionally counted within a category of the entity's type so the issue is
   * reported both in aggregate and broken down, e.g. routes lost per route type
   *
   * @param issue encountered
   * @param category within the entity type, may be null when the call site has none
   * @param entityId the GTFS id of the entity concerned, may be null when not entity specific
   * @param detailArgs the arguments the issue's detail template expects
   */
  public void registerIssue(
      final GtfsParseIssue issue, final Enum<?> category, final String entityId, final Object... detailArgs) {
    registerIssueOccurrence(issue, entityId, detailArgs);

    if (issue.isDiscarding()) {
      var index = discardedEntityIndex.get(issue.getEntityType());
      if (index != null && entityId != null) {
        index.put(entityId, issue);
      }
    }

    if (category != null) {
      collectCounter(collectCategoryCounters(issuesByCategory, issue), category.name()).increment();
    }
  }

  /**
   * Absorb everything another set of diagnostics recorded, so that stages run separately report as a single funnel.
   * <p>
   * Rejects a merge where both sides recorded the same GTFS entity type, since each type originates from one GTFS
   * file which should be read by one stage only. Were two stages to count the same file, the totals would double and
   * every share derived from them would be wrong, which is far harder to notice afterwards than a failed merge
   * </p>
   *
   * @param other to absorb, ignored when null
   */
  public void merge(final GtfsParseDiagnostics other) {
    merge(other, false /* reject an entity type recorded by both sides */);
  }

  /**
   * Absorb everything another set of diagnostics recorded
   *
   * @param other to absorb, ignored when null
   * @param allowEntityTypeOverlap when false a GTFS entity type recorded by both sides is rejected, when true the
   *          two are summed, which is only correct where the sides genuinely counted different entities of that type
   */
  public void merge(final GtfsParseDiagnostics other, final boolean allowEntityTypeOverlap) {
    if (other == null) {
      return;
    }
    if (!allowEntityTypeOverlap) {
      var overlap = collectRecordedEntityTypes();
      overlap.retainAll(other.collectRecordedEntityTypes());
      PlanItRunTimeException.throwIf(
          !overlap.isEmpty(),
          "Unable to merge GTFS parse diagnostics, both recorded GTFS entity type(s) %s, which would double their " +
              "totals; each type is expected to be read by a single stage",
          overlap.stream().map(Enum::name).collect(Collectors.joining(", ")));
    }

    other.seenByEntityType.forEach((entityType, adder) -> collectCounter(seenByEntityType, entityType).add(adder.sum()));
    other.seenByEntityTypeAndCategory.forEach((entityType, counters) -> counters.forEach(
        (category, adder) -> collectCounter(
            collectCategoryCounters(seenByEntityTypeAndCategory, entityType), category).add(adder.sum())));
    other.seenByEntityTypeAndScope.forEach((entityType, counters) -> counters.forEach(
        (scope, adder) -> registerSeenInScope(entityType, scope, adder.sum())));
    other.issuesByCategory.forEach((issue, counters) -> counters.forEach(
        (category, adder) -> collectCounter(
            collectCategoryCounters(issuesByCategory, issue), category).add(adder.sum())));

    discards.merge(other.discards);
    retainedIssues.merge(other.retainedIssues);

    other.discardedEntityIndex.forEach((entityType, index) -> {
      var ownIndex = discardedEntityIndex.get(entityType);
      if (ownIndex != null) {
        ownIndex.putAll(index);
      }
    });
  }

  /**
   * Collect every GTFS entity type this instance recorded anything for, whether entities seen or issues registered,
   * so a type that only ever produced discards still counts as recorded
   *
   * @return recorded entity types
   */
  private Set<GtfsObjectType> collectRecordedEntityTypes() {
    var recorded = EnumSet.noneOf(GtfsObjectType.class);
    seenByEntityType.forEach((entityType, adder) -> {
      if (adder.sum() > 0) {
        recorded.add(entityType);
      }
    });
    for (var issue : GtfsParseIssue.values()) {
      if (getOccurrences(issue) > 0) {
        recorded.add(issue.getEntityType());
      }
    }
    return recorded;
  }

  /**
   * Verify whether an entity was discarded. Only meaningful for the indexed entity types, for others false is always
   * returned since their discards are counted rather than indexed
   *
   * @param entityType of the entity
   * @param entityId to verify
   * @return true when registered as discarded, false otherwise
   */
  public boolean isDiscarded(final GtfsObjectType entityType, final String entityId) {
    return getDiscardIssue(entityType, entityId) != null;
  }

  /**
   * Collect the issue an entity was discarded for
   *
   * @param entityType of the entity
   * @param entityId to collect for
   * @return the issue responsible, null when the entity was not discarded or its type is not indexed
   */
  public GtfsParseIssue getDiscardIssue(final GtfsObjectType entityType, final String entityId) {
    var index = discardedEntityIndex.get(entityType);
    return index == null || entityId == null ? null : index.get(entityId);
  }

  /**
   * Collect how many entities of a type were encountered in the feed
   *
   * @param entityType to collect for
   * @return number seen
   */
  public long getSeen(final GtfsObjectType entityType) {
    var adder = seenByEntityType.get(entityType);
    return adder != null ? adder.sum() : 0;
  }

  /**
   * Collect how many entities of a type were encountered within a category of that type
   *
   * @param entityType to collect for
   * @param category to collect for
   * @return number seen
   */
  public long getSeen(final GtfsObjectType entityType, final Enum<?> category) {
    var counters = seenByEntityTypeAndCategory.get(entityType);
    if (counters == null || category == null) {
      return 0;
    }
    var adder = counters.get(category.name());
    return adder != null ? adder.sum() : 0;
  }

  /**
   * Collect how many entities of a type were encountered per category, ordered by category so what is reported is
   * stable between runs
   *
   * @param entityType to collect for
   * @return number seen per category, empty when the type was never registered with one
   */
  public SortedMap<String, Long> getSeenByCategory(final GtfsObjectType entityType) {
    return asSortedTotals(seenByEntityTypeAndCategory.get(entityType));
  }

  /**
   * Collect the categories an entity type was registered under, ordered by category
   *
   * @param entityType to collect for
   * @return categories
   */
  public Set<String> getCategories(final GtfsObjectType entityType) {
    return getSeenByCategory(entityType).keySet();
  }

  /**
   * Collect how often an issue was registered within a category
   *
   * @param issue to collect for
   * @param category to collect for
   * @return number of occurrences
   */
  public long getOccurrences(final GtfsParseIssue issue, final String category) {
    var counters = issuesByCategory.get(issue);
    if (counters == null || category == null) {
      return 0;
    }
    var adder = counters.get(category);
    return adder != null ? adder.sum() : 0;
  }

  /**
   * Collect how often an issue was registered per category, ordered by category
   *
   * @param issue to collect for
   * @return number of occurrences per category
   */
  public SortedMap<String, Long> getOccurrencesByCategory(final GtfsParseIssue issue) {
    return asSortedTotals(issuesByCategory.get(issue));
  }

  /**
   * Collect the issues of a type matching the given predicate on whether they discard and, optionally, their
   * disposition
   *
   * @param entityType to match
   * @param discarding whether the issues sought discard the entity
   * @param disposition to match, null to match any
   * @return matching issues
   */
  private static List<GtfsParseIssue> getIssues(
      final GtfsObjectType entityType, final boolean discarding, final GtfsIssueDisposition disposition) {
    return Arrays.stream(GtfsParseIssue.values()).filter(
        issue -> issue.getEntityType() == entityType && issue.isDiscarding() == discarding &&
            (disposition == null || issue.getDisposition() == disposition)).collect(Collectors.toList());
  }

  /**
   * Collect how many entities of a type were discarded, across all issues responsible
   *
   * @param entityType to collect for
   * @return number discarded
   */
  public long getDiscarded(final GtfsObjectType entityType) {
    return getIssues(entityType, true, null).stream().mapToLong(this::getOccurrences).sum();
  }

  /**
   * Collect how many entities of a type were discarded for issues of a given disposition
   *
   * @param entityType to collect for
   * @param disposition to collect for
   * @return number discarded
   */
  public long getDiscarded(final GtfsObjectType entityType, final GtfsIssueDisposition disposition) {
    return getIssues(entityType, true, disposition).stream().mapToLong(this::getOccurrences).sum();
  }

  /**
   * Collect how many entities of a type were discarded within a category, for issues of a given disposition
   *
   * @param entityType to collect for
   * @param category to collect for
   * @param disposition to collect for, null for any
   * @return number discarded
   */
  public long getDiscarded(
      final GtfsObjectType entityType, final String category, final GtfsIssueDisposition disposition) {
    return getIssues(entityType, true, disposition).stream().mapToLong(
        issue -> getOccurrences(issue, category)).sum();
  }

  /**
   * Collect how many entities of a type were discarded per category, ordered by category
   *
   * @param entityType to collect for
   * @return number discarded per category
   */
  public SortedMap<String, Long> getDiscardedByCategory(final GtfsObjectType entityType) {
    var totals = new TreeMap<String, Long>();
    getIssues(entityType, true, null).forEach(
        issue -> getOccurrencesByCategory(issue).forEach(
            (category, count) -> totals.merge(category, count, Long::sum)));
    return totals;
  }

  /**
   * Collect how many issues were registered against entities of a type that were parsed regardless. Counts
   * occurrences rather than entities, since one entity may carry several
   *
   * @param entityType to collect for
   * @return number of occurrences
   */
  public long getRetainedIssues(final GtfsObjectType entityType) {
    return getIssues(entityType, false, null).stream().mapToLong(this::getOccurrences).sum();
  }

  /**
   * Collect how many issues were registered within a category against entities of a type that were parsed regardless
   *
   * @param entityType to collect for
   * @param category to collect for
   * @return number of occurrences
   */
  public long getRetainedIssues(final GtfsObjectType entityType, final String category) {
    return getIssues(entityType, false, null).stream().mapToLong(
        issue -> getOccurrences(issue, category)).sum();
  }

  /**
   * Collect how many entities of a type were encountered and not discarded
   *
   * @param entityType to collect for
   * @return number parsed, never negative
   */
  public long getParsed(final GtfsObjectType entityType) {
    return Math.max(0, getSeen(entityType) - getDiscarded(entityType));
  }

  /**
   * Collect how many entities of a type were encountered within a category and not discarded
   *
   * @param entityType to collect for
   * @param category to collect for
   * @return number parsed, never negative
   */
  public long getParsed(final GtfsObjectType entityType, final String category) {
    return Math.max(0, getSeen(entityType, category) - getDiscarded(entityType, category, null));
  }

  /**
   * Collect how many entities of a type were encountered within a category
   *
   * @param entityType to collect for
   * @param category to collect for
   * @return number seen
   */
  private long getSeen(final GtfsObjectType entityType, final String category) {
    var counters = seenByEntityTypeAndCategory.get(entityType);
    if (counters == null || category == null) {
      return 0;
    }
    var adder = counters.get(category);
    return adder != null ? adder.sum() : 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    seenByEntityType.clear();
    seenByEntityTypeAndCategory.clear();
    seenByEntityTypeAndScope.clear();
    issuesByCategory.clear();
    discardedEntityIndex.values().forEach(Map::clear);
  }

  /**
   * Log what the feed holds and how much of it was ever in reach of the run, so that the denominator every share
   * below is measured against is stated rather than assumed.
   * <p>
   * An entity type whose scope could not be established is reported as such rather than silently counted as wholly in
   * reach, since the two look identical in the resulting percentages
   * </p>
   */
  private void logScopeSummary() {
    LOGGER.info(LoggingUtils.surroundWithBrackets("SCOPE") + "of the feed relative to the area covered");
    for (var entityType : GtfsObjectType.values()) {
      long seen = getSeen(entityType);
      if (seen == 0) {
        continue;
      }
      if (!hasScope(entityType)) {
        LOGGER.info(LoggingUtils.settingsValue(
            entityType.name().toLowerCase() + " in feed", seen + " (scope not established)", 1));
        continue;
      }
      LOGGER.info(LoggingUtils.settingsValue(
          entityType.name().toLowerCase() + " in reach",
          LoggingUtils.countWithPercentage(getSeenInReach(entityType), seen), 1));
    }
  }

  /**
   * Log what was in reach, then the discards grouped by stage, followed by the issues carried by entities that were
   * parsed regardless. Each line states its share of the entities of that type that were in reach, so a count is read
   * against what it could have been rather than in isolation
   */
  public void logSummary() {
    logScopeSummary();

    LOGGER.info(LoggingUtils.surroundWithBrackets("DISCARDS") + "by issue");
    boolean anyDiscard = false;
    for (var stage : GtfsParseStage.values()) {
      var stageIssues = GtfsParseIssue.getIssuesForStage(stage).stream().filter(
          issue -> issue.isDiscarding() && isReportedInSummary(issue)).collect(Collectors.toList());
      if (stageIssues.isEmpty()) {
        continue;
      }
      anyDiscard = true;
      LOGGER.info(LoggingUtils.settingsSection(stage.name(), 1));
      stageIssues.forEach(issue -> LOGGER.info(createIssueLogEntry(issue)));
    }
    if (!anyDiscard) {
      LOGGER.info(LoggingUtils.settingsEntry("none recorded", 1));
    }

    LOGGER.info(LoggingUtils.surroundWithBrackets("ISSUES") + "carried by parsed entities");
    var carried = Arrays.stream(GtfsParseIssue.values()).filter(
        issue -> !issue.isDiscarding() && isReportedInSummary(issue)).collect(Collectors.toList());
    if (carried.isEmpty()) {
      LOGGER.info(LoggingUtils.settingsEntry("none recorded", 1));
      return;
    }
    carried.forEach(issue -> LOGGER.info(createIssueLogEntry(issue)));
  }

  /**
   * Persist the full per entity detail behind the logged summary, so a collapsed log entry can still be traced back
   * to the entities that produced it
   *
   * @param outputDirectory to write the files to, created when absent
   */
  public void persist(final Path outputDirectory) {
    persistEntityIssues(
        outputDirectory.resolve("gtfs_discards.csv"), discards, GtfsIssueCsvColumn.getHeaders(),
        GtfsParseOutcome.DISCARDED);
    persistEntityIssues(
        outputDirectory.resolve("gtfs_issues.csv"), retainedIssues, GtfsIssueCsvColumn.getHeaders(),
        GtfsParseOutcome.PARSED);
    persistCoverageSummary(outputDirectory.resolve("gtfs_coverage_summary.csv"));
  }

  /**
   * Persist the totals the logged summary is derived from, one row per category an entity type was registered under
   * plus a row holding whatever was registered without one.
   * <p>
   * The rows of an entity type are disjoint, so summing them yields that type's totals. No row holding those totals is
   * written, since it would restate what the other rows already say.
   * </p>
   *
   * @param filePath to write to
   */
  private void persistCoverageSummary(final Path filePath) {
    try (var csvWriter = SimpleCsvWriter.create(filePath, GtfsCoverageCsvColumn.getHeaders())) {
      for (var entityType : GtfsObjectType.values()) {
        for (var category : collectReportedCategories(entityType)) {
          csvWriter.writeRow(
              entityType,
              category,
              getSeen(entityType, category),
              getParsed(entityType, category),
              getDiscarded(entityType, category, GtfsIssueDisposition.BY_DESIGN),
              getDiscarded(entityType, category, GtfsIssueDisposition.LIMITATION),
              getDiscarded(entityType, category, GtfsIssueDisposition.PROBLEM),
              getRetainedIssues(entityType, category));
        }
        persistUncategorisedRemainder(csvWriter, entityType);
      }
    }
  }

  /**
   * Persist what was registered for an entity type without a category, i.e. its totals less what the category rows
   * already account for. Entity types no call site categorises, such as stop times, are carried entirely by this row,
   * which is why it is written rather than the category rows being assumed to be exhaustive
   *
   * @param csvWriter to write with
   * @param entityType to write the remainder for
   */
  private void persistUncategorisedRemainder(
      final SimpleCsvWriter csvWriter, final GtfsObjectType entityType) {
    var categories = collectReportedCategories(entityType);

    long seen = getSeen(entityType) - sumOverCategories(categories, category -> getSeen(entityType, category));
    long byDesign = getDiscarded(entityType, GtfsIssueDisposition.BY_DESIGN) - sumOverCategories(
        categories, category -> getDiscarded(entityType, category, GtfsIssueDisposition.BY_DESIGN));
    long limitation = getDiscarded(entityType, GtfsIssueDisposition.LIMITATION) - sumOverCategories(
        categories, category -> getDiscarded(entityType, category, GtfsIssueDisposition.LIMITATION));
    long problem = getDiscarded(entityType, GtfsIssueDisposition.PROBLEM) - sumOverCategories(
        categories, category -> getDiscarded(entityType, category, GtfsIssueDisposition.PROBLEM));
    long issuesOnParsed = getRetainedIssues(entityType) - sumOverCategories(
        categories, category -> getRetainedIssues(entityType, category));

    if (seen == 0 && byDesign == 0 && limitation == 0 && problem == 0 && issuesOnParsed == 0) {
      /* the category rows account for everything this type recorded, so a remainder row would hold only zeroes */
      return;
    }

    csvWriter.writeRow(
        entityType,
        null,
        seen,
        Math.max(0, seen - (byDesign + limitation + problem)),
        byDesign,
        limitation,
        problem,
        issuesOnParsed);
  }

  /**
   * Total a value across the given categories
   *
   * @param categories to total across
   * @param valueFor supplying the value of a single category
   * @return total
   */
  private static long sumOverCategories(
      final Set<String> categories, final ToLongFunction<String> valueFor) {
    return categories.stream().mapToLong(valueFor).sum();
  }

  /**
   * Collect every category an entity type was reported under, whether through entities seen or issues registered, so
   * a category that only ever produced discards is still reported
   *
   * @param entityType to collect for
   * @return categories, ordered by name
   */
  private Set<String> collectReportedCategories(final GtfsObjectType entityType) {
    var categories = new TreeSet<String>(getSeenByCategory(entityType).keySet());
    categories.addAll(getDiscardedByCategory(entityType).keySet());
    return categories;
  }
}
