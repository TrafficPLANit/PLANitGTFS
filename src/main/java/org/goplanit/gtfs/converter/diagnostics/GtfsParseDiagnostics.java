package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.utils.csv.SimpleCsvWriter;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.LogCollator;
import org.goplanit.utils.misc.LoggingUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
 * against a subType within that type such as the route type a route belongs to. The same entity therefore appears in
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

  /** names the denominator holding everything the feed holds, used by the log and the written summary alike */
  private static final String DENOMINATOR_BASIS_IN_FEED = "feed";

  /** names the denominator holding what was ever within the area the run covers */
  private static final String DENOMINATOR_BASIS_WITHIN_AREA = "area";

  /** stands in for a subType where a call site supplied none, a map needing a key either way */
  private static final String NO_SUBTYPE = "";

  /**
   * How many GTFS entities of each type were encountered, within their subType and their scope.
   * <p>
   * One tally rather than one per axis. Every entity encountered occupies exactly one cell of it and moves between
   * cells as its scope settles, so what the feed holds, what has a scope and what was within the area are all read off the
   * same counters and cannot come to disagree
   * </p>
   */
  private final Map<GtfsObjectType, Map<String, Map<GtfsEntityScope, LongAdder>>> seenByEntityTypeSubTypeAndScope =
      new ConcurrentHashMap<>();

  /**
   * How each issue stands in relation to the scope of the entities it arises for.
   * <p>
   * Established as the issues are registered rather than kept as a hand maintained list, so an issue added later
   * classifies itself. An issue first seen before its type has any scope stands `PRE_SCOPE`; one ever seen against an
   * entity known to be beyond the area widens to `ANY_SCOPE`; what survives neither arises only for entities within the area
   * </p>
   */
  private final Map<GtfsParseIssue, GtfsIssueScopeRelation> scopeRelationByIssue = new ConcurrentHashMap<>();

  /** the scope settled for individual entities, for the indexed entity types only */
  private final Map<GtfsObjectType, Map<String, GtfsEntityScope>> scopeByEntityId =
      new EnumMap<>(GtfsObjectType.class);

  /**
   * How often each issue was registered, per subType where one was supplied and per the scope the entity stood in at
   * the moment it arose.
   * <p>
   * The scope makes the classification checkable rather than asserted: an issue firing only against entities within the area
   * is a measure of the parser, one firing against entities beyond the area is a measure of the ground the feed
   * covers, and the rows say which without anyone having to reason it out
   * </p>
   */
  private final Map<GtfsParseIssue, Map<String, Map<GtfsEntityScope, LongAdder>>> issuesBySubTypeAndScope =
      new ConcurrentHashMap<>();

  /** issues that cost the entity, keyed by issue name */
  private final LogCollator discards;

  /** issues carried by an entity that was parsed regardless, keyed by issue name */
  private final LogCollator retainedIssues;

  /**
   * The subtype each entity was seen under, keyed by id, for the indexed entity types only.
   * <p>
   * An entity is not always discarded where it was read. A route left without trips is pruned long after
   * routes.txt was parsed, by which point its route type is no longer at hand, yet counting that discard
   * outside the route type the route was seen under leaves the two sides of the report unable to reconcile.
   * Recording the subtype once, here, lets any later discard be attributed to it.
   * </p>
   */
  private final Map<GtfsObjectType, Map<String, String>> seenSubTypeByEntityId = new EnumMap<>(GtfsObjectType.class);

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
    INDEXED_ENTITY_TYPES.forEach(type -> seenSubTypeByEntityId.put(type, new ConcurrentHashMap<>()));
    INDEXED_ENTITY_TYPES.forEach(type -> scopeByEntityId.put(type, new ConcurrentHashMap<>()));
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
   * Measured against the entities of the type that were within the area the run covers, where the scope of that type was
   * established. Anything beyond the area could never have produced the issue, so counting it in the denominator only
   * makes every share look smaller than it is.
   * </p>
   * <p>
   * Two cases keep the full total instead. An entity type whose scope was never established has no within the area figure to
   * divide by. And an issue that is itself what places an entity beyond the area arises precisely for the entities an in
   * reach total excludes, so measuring it against that total would report shares far beyond 100%.
   * </p>
   */
  @Override
  protected long getDenominator(final GtfsParseIssue issue) {
    var entityType = issue.getEntityType();
    return isMeasuredAgainstFeed(issue) ? getSeenInFeed(entityType) : getSeenWithinArea(entityType);
  }

  /**
   * Collect how an issue stands in relation to the scope of the entities it arises for, which is what decides the
   * population its occurrences can be measured against
   *
   * @param issue to collect for
   * @return scope relation
   */
  public GtfsIssueScopeRelation getScopeRelation(final GtfsParseIssue issue) {
    var relation = scopeRelationByIssue.get(issue);
    if (relation == null) {
      return hasScope(issue.getEntityType())
          ? GtfsIssueScopeRelation.WITHIN_AREA : GtfsIssueScopeRelation.PRE_SCOPE;
    }
    /* a type that never gained scope leaves nothing within the area to measure against, whatever was observed */
    return hasScope(issue.getEntityType()) ? relation : GtfsIssueScopeRelation.PRE_SCOPE;
  }

  /**
   * Verify whether an issue's occurrences are to be measured against what was within the area
   *
   * @param issue to verify for
   * @return true when measured against what was within the area, false when measured against the feed
   */
  private boolean isMeasuredAgainstFeed(final GtfsParseIssue issue) {
    return !getScopeRelation(issue).isMeasuredAgainstEntitiesWithinArea();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected long getDenominator(final GtfsParseIssue issue, final String subType) {
    var entityType = issue.getEntityType();
    return isMeasuredAgainstFeed(issue)
        ? getSeenInFeed(entityType, subType) : getSeenWithinArea(entityType, subType);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDenominatorLabel(final GtfsParseIssue issue) {
    return isMeasuredAgainstFeed(issue) ? DENOMINATOR_BASIS_IN_FEED : DENOMINATOR_BASIS_WITHIN_AREA;
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
        occurrence.getEntitySubType(),
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
    registerSeen(entityType, (Enum<?>) null, count);
  }

  /**
   * Collect the counter holding the entities of a type encountered within a subType and a scope, creating it when
   * genuinely absent
   *
   * @param entityType to collect for
   * @param subType to collect for, null when none was supplied
   * @param scope to collect for
   * @return counter
   */
  private LongAdder collectSeenCounter(
      final GtfsObjectType entityType, final String subType, final GtfsEntityScope scope) {
    return seenByEntityTypeSubTypeAndScope
        .computeIfAbsent(entityType, type -> new ConcurrentHashMap<>())
        .computeIfAbsent(subType != null ? subType : NO_SUBTYPE, type -> new ConcurrentHashMap<>())
        .computeIfAbsent(scope, type -> new LongAdder());
  }

  /**
   * Register that an entity of the given type was encountered, counted both against its type and within a subType of
   * that type, e.g. a route within its route type
   *
   * @param entityType encountered
   * @param subType within the entity type
   */
  public void registerSeen(final GtfsObjectType entityType, final Enum<?> subType) {
    registerSeen(entityType, subType, 1);
  }

  /**
   * Register that an entity of the given type was encountered within a subtype, remembering the subtype against the
   * entity so that a discard registered later, away from where the entity was read, can still be counted within it
   *
   * @param entityType encountered
   * @param subType within the entity type
   * @param entityId identifying the entity
   */
  public void registerSeen(final GtfsObjectType entityType, final Enum<?> subType, final String entityId) {
    registerSeen(entityType, subType, 1);

    var index = seenSubTypeByEntityId.get(entityType);
    if (index != null && entityId != null && subType != null) {
      index.put(entityId, subType.name());
    }
  }

  /**
   * Collect the scope settled for an individual entity
   *
   * @param entityType to collect for
   * @param entityId to collect for
   * @return scope, null where none was settled for it
   */
  public GtfsEntityScope getSettledScope(final GtfsObjectType entityType, final String entityId) {
    var index = scopeByEntityId.get(entityType);
    return index == null || entityId == null ? null : index.get(entityId);
  }

  /**
   * Collect the subtype an entity was seen under
   *
   * @param entityType to collect for
   * @param entityId to collect for
   * @return subtype, null when none was recorded for it
   */
  public String getSeenSubType(final GtfsObjectType entityType, final String entityId) {
    var index = seenSubTypeByEntityId.get(entityType);
    return index == null || entityId == null ? null : index.get(entityId);
  }

  /**
   * Register that a number of entities of the given type were encountered, counted both against their type and within
   * a subType of that type
   *
   * @param entityType encountered
   * @param subType within the entity type
   * @param count how many
   */
  public void registerSeen(final GtfsObjectType entityType, final Enum<?> subType, final long count) {
    /* where the entity sits is not known at the point it is encountered, so it is held apart until it is, or stays
     * there when the entity is dropped before it could be told */
    collectSeenCounter(entityType, subType != null ? subType.name() : null, GtfsEntityScope.NOT_ESTABLISHED)
        .add(count);
  }

  /**
   * Register that an entity of the given type was encountered within a subType and with its scope already settled, as
   * is the case where what the entity is and where it sits are both known the moment it is read
   *
   * @param entityType encountered
   * @param subType within the entity type, may be null
   * @param scope of the entity relative to the area the run covers
   */
  public void registerSeen(final GtfsObjectType entityType, final Enum<?> subType, final GtfsEntityScope scope) {
    collectSeenCounter(entityType, subType != null ? subType.name() : null, scope).increment();
  }

  /**
   * Register that a part of an entity was encountered within or beyond the area the run covers, the entity's own scope
   * following from every part registered for it: wholly within, wholly beyond, or partly both.
   * <p>
   * An entity spanning several rows of a file has its scope settled only once all of them have been seen, a trip
   * running from inside the area to beyond it being neither. Accumulating that here rather than in the handler keeps
   * the rule in one place and spares each handler from having to tell where one entity ends and the next begins
   * </p>
   * <p>
   * The entity moves between cells of the tally as its scope settles, so it is counted exactly once throughout
   * </p>
   *
   * @param entityType encountered
   * @param entityId identifying the entity the part belongs to
   * @param partScope of the part encountered
   */
  public void registerSeenPartInScope(
      final GtfsObjectType entityType, final String entityId, final GtfsEntityScope partScope) {
    var index = scopeByEntityId.get(entityType);
    if (index == null || entityId == null || partScope == null) {
      return;
    }

    var knownScope = index.getOrDefault(entityId, GtfsEntityScope.NOT_ESTABLISHED);
    var settledScope = !knownScope.isEstablished()
        ? partScope
        : (knownScope == partScope ? knownScope : GtfsEntityScope.PARTIAL);
    if (settledScope == knownScope) {
      return;
    }

    var subType = getSeenSubType(entityType, entityId);
    var previousCounter = collectSeenCounter(entityType, subType, knownScope);
    if (previousCounter.sum() <= 0) {
      /* the entity was never counted as encountered, so moving it would put the tally into deficit and misreport
       * every share measured against it. Left where it is, the mismatch surfacing as an unsettled scope instead */
      return;
    }

    previousCounter.decrement();
    collectSeenCounter(entityType, subType, settledScope).increment();
    index.put(entityId, settledScope);
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
   * Register an issue against an entity, additionally counted within a subType of the entity's type so the issue is
   * reported both in aggregate and broken down, e.g. routes lost per route type
   *
   * @param issue encountered
   * @param subType within the entity type, may be null when the call site has none
   * @param entityId the GTFS id of the entity concerned, may be null when not entity specific
   * @param detailArgs the arguments the issue's detail template expects
   */
  public void registerIssue(
      final GtfsParseIssue issue, final Enum<?> subType, final String entityId, final Object... detailArgs) {
    registerIssue(issue, subType, null, entityId, detailArgs);
  }

  /**
   * Register an issue against an entity whose scope the call site knows, which an entity not indexed individually can
   * only be accounted for by stating
   *
   * @param issue encountered
   * @param subType within the entity type, may be null when the call site has none
   * @param scope the entity stood in when the issue arose, null to recover whatever was settled for it
   * @param entityId the GTFS id of the entity concerned, may be null when not entity specific
   * @param detailArgs the arguments the issue's detail template expects
   */
  public void registerIssue(
      final GtfsParseIssue issue, final Enum<?> subType, final GtfsEntityScope scope, final String entityId,
      final Object... detailArgs) {
    /* the scope the entity stood in as this arose, stated by the call site where the entity is not indexed
     * individually and recovered otherwise */
    var occurrenceScope = scope;
    if (occurrenceScope == null) {
      occurrenceScope = getSettledScope(issue.getEntityType(), entityId);
    }
    if (occurrenceScope == null) {
      occurrenceScope = GtfsEntityScope.NOT_ESTABLISHED;
    }

    /* where the issue stands relative to scope, settled as its occurrences arrive rather than declared per issue */
    var observedRelation = !occurrenceScope.isEstablished()
        ? GtfsIssueScopeRelation.PRE_SCOPE
        : (occurrenceScope.isWithinArea()
            ? GtfsIssueScopeRelation.WITHIN_AREA : GtfsIssueScopeRelation.ANY_SCOPE);
    scopeRelationByIssue.merge(issue, observedRelation, GtfsIssueScopeRelation::widen);

    /* a caller that cannot name the subType falls back on the one the entity was seen under, so that a discard
     * registered away from where the entity was read still lands in the same subType as what was counted */
    var subTypeName = subType != null ? subType.name() : getSeenSubType(issue.getEntityType(), entityId);
    registerIssueOccurrence(issue, entityId, subTypeName, detailArgs);

    if (issue.isDiscarding()) {
      var index = discardedEntityIndex.get(issue.getEntityType());
      if (index != null && entityId != null) {
        index.put(entityId, issue);
      }
    }

    issuesBySubTypeAndScope
        .computeIfAbsent(issue, registered -> new ConcurrentHashMap<>())
        .computeIfAbsent(subTypeName != null ? subTypeName : NO_SUBTYPE, type -> new ConcurrentHashMap<>())
        .computeIfAbsent(occurrenceScope, type -> new LongAdder()).increment();
  }

  /**
   * Verify whether an entity of the given type was discarded
   *
   * @param entityType of the entity
   * @param entityId of the entity
   * @return true when discarded, false otherwise
   */
  public boolean isDiscarded(final GtfsObjectType entityType, final String entityId) {
    return getDiscardIssue(entityType, entityId) != null;
  }

  /**
   * Collect the issue an entity of the given type was discarded for
   *
   * @param entityType of the entity
   * @param entityId of the entity
   * @return issue responsible, null when the entity was not discarded
   */
  public GtfsParseIssue getDiscardIssue(final GtfsObjectType entityType, final String entityId) {
    var index = discardedEntityIndex.get(entityType);
    return index == null || entityId == null ? null : index.get(entityId);
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

    other.seenByEntityTypeSubTypeAndScope.forEach((entityType, subTypeCounters) -> subTypeCounters.forEach(
        (subType, scopeCounters) -> scopeCounters.forEach(
            (scope, adder) -> collectSeenCounter(entityType, subType, scope).add(adder.sum()))));
    other.scopeRelationByIssue.forEach(
        (issue, relation) -> scopeRelationByIssue.merge(issue, relation, GtfsIssueScopeRelation::widen));
    other.issuesBySubTypeAndScope.forEach((issue, subTypeCounters) -> subTypeCounters.forEach(
        (subType, scopeCounters) -> scopeCounters.forEach((scope, adder) -> issuesBySubTypeAndScope
            .computeIfAbsent(issue, registered -> new ConcurrentHashMap<>())
            .computeIfAbsent(subType, type -> new ConcurrentHashMap<>())
            .computeIfAbsent(scope, type -> new LongAdder()).add(adder.sum()))));

    discards.merge(other.discards);
    retainedIssues.merge(other.retainedIssues);

    other.discardedEntityIndex.forEach((entityType, index) -> {
      var ownIndex = discardedEntityIndex.get(entityType);
      if (ownIndex != null) {
        ownIndex.putAll(index);
      }
    });
    other.seenSubTypeByEntityId.forEach((entityType, index) -> {
      var ownIndex = seenSubTypeByEntityId.get(entityType);
      if (ownIndex != null) {
        ownIndex.putAll(index);
      }
    });
    other.scopeByEntityId.forEach((entityType, index) -> {
      var ownIndex = scopeByEntityId.get(entityType);
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
    seenByEntityTypeSubTypeAndScope.keySet().forEach(entityType -> {
      if (getSeenInFeed(entityType) > 0) {
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
   * Collect how many entities of a type and, where given, a subType, were encountered with the given scope
   *
   * @param entityType to collect for
   * @param subType to collect for, null to total across every subType
   * @param scope to collect for
   * @return number encountered
   */
  public long getSeenInScope(
      final GtfsObjectType entityType, final String subType, final GtfsEntityScope scope) {
    var subTypeCounters = seenByEntityTypeSubTypeAndScope.get(entityType);
    if (subTypeCounters == null) {
      return 0;
    }
    return subTypeCounters.entrySet().stream()
        .filter(entry -> subType == null || entry.getKey().equals(subType))
        .mapToLong(entry -> {
          var counter = entry.getValue().get(scope);
          return counter != null ? counter.sum() : 0;
        }).sum();
  }

  /**
   * Collect how many entities of a type were encountered with the given scope
   *
   * @param entityType to collect for
   * @param scope to collect for
   * @return number encountered
   */
  public long getSeenInScope(final GtfsObjectType entityType, final GtfsEntityScope scope) {
    return getSeenInScope(entityType, null, scope);
  }

  /**
   * Collect how many entities of a type the feed holds, irrespective of whether their scope was ever settled. This is
   * every row of the file the type comes from
   *
   * @param entityType to collect for
   * @return number in the feed
   */
  public long getSeenInFeed(final GtfsObjectType entityType) {
    return getSeenInFeed(entityType, (String) null);
  }

  /**
   * Collect how many entities of a type and subType the feed holds
   *
   * @param entityType to collect for
   * @param subType to collect for, null to total across every subType
   * @return number in the feed
   */
  public long getSeenInFeed(final GtfsObjectType entityType, final String subType) {
    return Arrays.stream(GtfsEntityScope.values()).mapToLong(
        scope -> getSeenInScope(entityType, subType, scope)).sum();
  }

  /**
   * Collect how many entities of a type and subType the feed holds
   *
   * @param entityType to collect for
   * @param subType to collect for
   * @return number in the feed
   */
  public long getSeenInFeed(final GtfsObjectType entityType, final Enum<?> subType) {
    return getSeenInFeed(entityType, subType != null ? subType.name() : null);
  }

  /**
   * Collect how many entities of a type had their scope settled at all, i.e. reached the point where where they sit
   * could be told. What the feed holds less this is what was dropped before that point was ever reached
   *
   * @param entityType to collect for
   * @return number with a settled scope
   */
  public long getSeenWithScope(final GtfsObjectType entityType) {
    return getSeenWithScope(entityType, null);
  }

  /**
   * Collect how many entities of a type and, where given, a subType, had their scope settled at all
   *
   * @param entityType to collect for
   * @param subType to collect for, null to total across every subType
   * @return number with a settled scope
   */
  public long getSeenWithScope(final GtfsObjectType entityType, final String subType) {
    return Arrays.stream(GtfsEntityScope.values()).filter(GtfsEntityScope::isEstablished).mapToLong(
        scope -> getSeenInScope(entityType, subType, scope)).sum();
  }

  /**
   * Collect how many entities of a type were ever within the area of the run, i.e. wholly or partly within the area it
   * covers. This is the denominator anything the parser achieved should be measured against
   *
   * @param entityType to collect for
   * @return number within the area
   */
  public long getSeenWithinArea(final GtfsObjectType entityType) {
    return getSeenWithinArea(entityType, null);
  }

  /**
   * Collect how many entities of a type and, where given, a subType, were ever within the area of the run
   *
   * @param entityType to collect for
   * @param subType to collect for, null to total across every subType
   * @return number within the area
   */
  public long getSeenWithinArea(final GtfsObjectType entityType, final String subType) {
    return Arrays.stream(GtfsEntityScope.values()).filter(GtfsEntityScope::isWithinArea).mapToLong(
        scope -> getSeenInScope(entityType, subType, scope)).sum();
  }

  /**
   * Verify whether the scope of entities of a type was established at all. Where it was not, every entity of that type
   * has to be treated as within the area, there being nothing to say otherwise
   *
   * @param entityType to verify for
   * @return true when scope was recorded, false otherwise
   */
  public boolean hasScope(final GtfsObjectType entityType) {
    return getSeenWithScope(entityType) > 0;
  }

  /**
   * Collect how many entities of a type were encountered per subType, ordered by subType so what is reported is
   * stable between runs
   *
   * @param entityType to collect for
   * @return number seen per subType, empty when the type was never registered with one
   */
  public SortedMap<String, Long> getSeenBySubType(final GtfsObjectType entityType) {
    var totals = new TreeMap<String, Long>();
    var subTypeCounters = seenByEntityTypeSubTypeAndScope.get(entityType);
    if (subTypeCounters == null) {
      return totals;
    }
    subTypeCounters.keySet().stream().filter(subType -> !NO_SUBTYPE.equals(subType)).forEach(
        subType -> totals.put(subType, getSeenInFeed(entityType, subType)));
    return totals;
  }

  /**
   * Collect the subTypes an entity type was registered under, ordered by subType
   *
   * @param entityType to collect for
   * @return subTypes
   */
  public Set<String> getSubTypes(final GtfsObjectType entityType) {
    return getSeenBySubType(entityType).keySet();
  }

  /**
   * Collect how often an issue was registered within a subType
   *
   * @param issue to collect for
   * @param subType to collect for
   * @return number of occurrences
   */
  public long getOccurrences(final GtfsParseIssue issue, final String subType) {
    var subTypeCounters = issuesBySubTypeAndScope.get(issue);
    if (subTypeCounters == null || subType == null) {
      return 0;
    }
    var scopeCounters = subTypeCounters.get(subType);
    return scopeCounters == null ? 0 : scopeCounters.values().stream().mapToLong(LongAdder::sum).sum();
  }

  /**
   * Collect how often an issue was registered within a subType against entities standing in the given scope
   *
   * @param issue to collect for
   * @param subType to collect for
   * @param scope to collect for
   * @return number of occurrences
   */
  public long getOccurrences(
      final GtfsParseIssue issue, final String subType, final GtfsEntityScope scope) {
    var subTypeCounters = issuesBySubTypeAndScope.get(issue);
    if (subTypeCounters == null || subType == null) {
      return 0;
    }
    var scopeCounters = subTypeCounters.get(subType);
    if (scopeCounters == null) {
      return 0;
    }
    var adder = scopeCounters.get(scope);
    return adder != null ? adder.sum() : 0;
  }

  /**
   * Collect the subTypes an issue was registered under, ordered by subType
   *
   * @param issue to collect for
   * @return subTypes
   */
  public Set<String> getSubTypesOf(final GtfsParseIssue issue) {
    var subTypeCounters = issuesBySubTypeAndScope.get(issue);
    return subTypeCounters == null ? Collections.emptySet() : new TreeSet<>(subTypeCounters.keySet());
  }

  /**
   * Collect how often an issue was registered per subType, ordered by subType
   *
   * @param issue to collect for
   * @return number of occurrences per subType
   */
  @Override
  protected Map<String, Long> getOccurrencesByScope(final GtfsParseIssue issue) {
    /* kept in the order the scopes are declared, which runs from wholly within to beyond and then to unsettled, an
     * alphabetical ordering putting them in an order that means nothing */
    var totals = new LinkedHashMap<String, Long>();
    for (var scope : GtfsEntityScope.values()) {
      long occurrences = getSubTypesOf(issue).stream().mapToLong(
          subType -> getOccurrences(issue, subType, scope)).sum();
      if (occurrences > 0) {
        totals.put(scope.name(), occurrences);
      }
    }
    return totals;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SortedMap<String, Long> getOccurrencesBySubType(final GtfsParseIssue issue) {
    var totals = new TreeMap<String, Long>();
    getSubTypesOf(issue).stream().filter(subType -> !NO_SUBTYPE.equals(subType)).forEach(
        subType -> totals.put(subType, getOccurrences(issue, subType)));
    return totals;
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
   * Collect how many entities of a type were discarded within a subType, for issues of a given disposition
   *
   * @param entityType to collect for
   * @param subType to collect for
   * @param disposition to collect for, null for any
   * @return number discarded
   */
  public long getDiscarded(
      final GtfsObjectType entityType, final String subType, final GtfsIssueDisposition disposition) {
    return getIssues(entityType, true, disposition).stream().mapToLong(
        issue -> getOccurrences(issue, subType)).sum();
  }

  /**
   * Collect how many entities of a type were discarded per subType, ordered by subType
   *
   * @param entityType to collect for
   * @return number discarded per subType
   */
  public SortedMap<String, Long> getDiscardedBySubType(final GtfsObjectType entityType) {
    var totals = new TreeMap<String, Long>();
    getIssues(entityType, true, null).forEach(
        issue -> getOccurrencesBySubType(issue).forEach(
            (subType, count) -> totals.merge(subType, count, Long::sum)));
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
   * Collect how many issues were registered within a subType against entities of a type that were parsed regardless
   *
   * @param entityType to collect for
   * @param subType to collect for
   * @return number of occurrences
   */
  public long getRetainedIssues(final GtfsObjectType entityType, final String subType) {
    return getIssues(entityType, false, null).stream().mapToLong(
        issue -> getOccurrences(issue, subType)).sum();
  }

  /**
   * Collect how many entities of a type were encountered and not discarded
   *
   * @param entityType to collect for
   * @return number parsed, never negative
   */
  public long getParsed(final GtfsObjectType entityType) {
    return Math.max(0, getSeenInFeed(entityType) - getDiscarded(entityType));
  }

  /**
   * Collect how many entities of a type were encountered within a subType and not discarded
   *
   * @param entityType to collect for
   * @param subType to collect for
   * @return number parsed, never negative
   */
  public long getParsed(final GtfsObjectType entityType, final String subType) {
    return Math.max(0, getSeenInFeed(entityType, subType) - getDiscarded(entityType, subType, null));
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    seenByEntityTypeSubTypeAndScope.clear();
    scopeByEntityId.values().forEach(Map::clear);
    issuesBySubTypeAndScope.clear();
    scopeRelationByIssue.clear();
    seenSubTypeByEntityId.values().forEach(Map::clear);
    discardedEntityIndex.values().forEach(Map::clear);
  }

  /**
   * Log what the feed holds and how much of it was ever within the area of the run, so that the denominator every share
   * below is measured against is stated rather than assumed.
   * <p>
   * An entity type whose scope could not be established is reported as such rather than silently counted as wholly in
   * reach, since the two look identical in the resulting percentages
   * </p>
   */
  private void logScopeSummary() {
    LOGGER.info(LoggingUtils.surroundWithBrackets("SCOPE") + "of the feed relative to the area covered");
    for (var entityType : GtfsObjectType.values()) {
      long seen = getSeenInFeed(entityType);
      if (seen == 0) {
        continue;
      }
      /* the same shape the issue entries take, an entity label and what is being said about it, so the two blocks
       * read as one report rather than two */
      var label = String.format("%s | %ss", entityType.name().toLowerCase(), entityType.name().toLowerCase());
      if (!hasScope(entityType)) {
        LOGGER.info(LoggingUtils.settingsValue(
            label + " in feed", seen + " (scope not established)", 1));
        continue;
      }

      /* stated as a funnel, since an entity dropped before its scope could be told is neither within the area nor out of it,
       * and folding it into either would misstate both */
      long withScope = getSeenWithScope(entityType);
      LOGGER.info(LoggingUtils.settingsValue(label + " in feed", String.valueOf(seen), 1));
      if (withScope < seen) {
        LOGGER.info(LoggingUtils.settingsValue(
            label + " with settled scope",
            LoggingUtils.countWithPercentage(withScope, seen, "feed"), 1));
      }
      /* the three shares of the settled population, which sum to 100% and of which the first two are what within the area
       * adds up to, so the line states not only how much was within the area but how it got there */
      var withinArea = new StringBuilder(
          LoggingUtils.countWithPercentage(getSeenWithinArea(entityType), withScope, "scope settled"));
      withinArea.append("  [");
      withinArea.append(Arrays.stream(GtfsEntityScope.values()).filter(GtfsEntityScope::isEstablished).map(
          scope -> String.format(
              "%s %.2f%%", scope.name(),
              withScope > 0 ? (100.0 * getSeenInScope(entityType, scope)) / withScope : 0.0)).collect(
          Collectors.joining(", ")));
      withinArea.append("]");
      LOGGER.info(LoggingUtils.settingsValue(label + " within area (IN or PARTIAL)", withinArea.toString(), 1));
    }
  }

  /**
   * Log what was within the area, then the discards grouped by stage, followed by the issues carried by entities that were
   * parsed regardless. Each line states its share of the entities of that type that were within the area, so a count is read
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
      stageIssues.forEach(this::logIssue);
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
    carried.forEach(this::logIssue);
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
    persistIssueSummary(outputDirectory.resolve("gtfs_issue_summary.csv"));
  }


  /**
   * Persist one row per issue and, where its entity type is subdivided, per subtype it arose within, stating what each
   * share was measured against.
   * <p>
   * The logged summary collapses an issue to a single line so that it stays readable; a share there can only name the
   * population it was measured against. Here the denominator and what it holds are written out, so a figure read from
   * the report can be checked rather than taken on trust
   * </p>
   *
   * @param filePath to write to
   */
  private void persistIssueSummary(final Path filePath) {
    try (var csvWriter = SimpleCsvWriter.create(filePath, GtfsIssueSummaryCsvColumn.getHeaders())) {
      for (var issue : GtfsParseIssue.values()) {
        if (getOccurrences(issue) == 0) {
          continue;
        }
        var outcome = issue.isDiscarding() ? GtfsParseOutcome.DISCARDED : GtfsParseOutcome.PARSED;
        /* the rows are disjoint and sum to the issue's total, so no row restating that total is written */
        for (var subType : getSubTypesOf(issue)) {
          for (var scope : GtfsEntityScope.values()) {
            long occurrences = getOccurrences(issue, subType, scope);
            if (occurrences > 0) {
              writeIssueSummaryRow(
                  csvWriter, issue, NO_SUBTYPE.equals(subType) ? null : subType, scope, occurrences, outcome);
            }
          }
        }
      }
    }
  }

  /**
   * Write a single row of the issue summary, naming the population the occurrences are to be measured against
   * rather than the total itself, that being a sum over the coverage tally
   *
   * @param csvWriter to write with
   * @param issue the row reports on
   * @param subType the row reports on, null where the entity type is not subdivided
   * @param scope the entities stood in when the issue arose
   * @param occurrences of the issue within the subtype and scope
   * @param outcome what became of the entities it arose for
   */
  private void writeIssueSummaryRow(
      final SimpleCsvWriter csvWriter, final GtfsParseIssue issue, final String subType, final GtfsEntityScope scope,
      final long occurrences, final GtfsParseOutcome outcome) {
    csvWriter.writeRow(
        issue.getStage(),
        issue.getEntityType(),
        subType,
        scope,
        issue.name(),
        issue.getDisposition(),
        outcome.getLabel(),
        occurrences,
        getScopeRelation(issue));
  }

  /**
   * Persist the tally the whole report is derived from, one row per entity type, subType and scope.
   * <p>
   * Written as the raw counts rather than as the populations the log speaks in. What the feed holds, what reached the
   * point its scope could be told and what was within the area are each a sum over these rows, so a reader can take whichever
   * grouping is of interest instead of being limited to the three the log happens to print
   * </p>
   * <p>
   * The unsettled scope is written along with the rest. Without it the rows would not sum to what the feed holds,
   * which is the one total every other figure is read against
   * </p>
   *
   * @param filePath to write to
   */
  private void persistCoverageSummary(final Path filePath) {
    try (var csvWriter = SimpleCsvWriter.create(filePath, GtfsCoverageCsvColumn.getHeaders())) {
      for (var entityType : GtfsObjectType.values()) {
        var subTypeCounters = seenByEntityTypeSubTypeAndScope.get(entityType);
        if (subTypeCounters == null) {
          continue;
        }
        new TreeSet<>(subTypeCounters.keySet()).forEach(subType -> {
          for (var scope : GtfsEntityScope.values()) {
            long count = getSeenInScope(entityType, subType, scope);
            if (count > 0) {
              csvWriter.writeRow(
                  entityType, NO_SUBTYPE.equals(subType) ? null : subType, scope, count);
            }
          }
        });
      }
    }
  }


  /**
   * Collect every subType an entity type was reported under, whether through entities seen or issues registered, so
   * a subType that only ever produced discards is still reported
   *
   * @param entityType to collect for
   * @return subTypes, ordered by name
   */
  private Set<String> collectReportedSubTypes(final GtfsObjectType entityType) {
    var subTypes = new TreeSet<String>(getSeenBySubType(entityType).keySet());
    subTypes.addAll(getDiscardedBySubType(entityType).keySet());
    return subTypes;
  }
}
