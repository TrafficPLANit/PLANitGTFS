package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.utils.csv.SimpleCsvWriter;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.LogCollator;
import org.goplanit.utils.misc.LoggingUtils;

import java.nio.file.Path;
import java.util.ArrayList;
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
  private final Map<GtfsObjectType, Map<String, Map<GtfsScopeState, LongAdder>>> seenByEntityTypeSubTypeAndScope =
      new ConcurrentHashMap<>();

  /** the scope settled for individual entities, for the indexed entity types only */
  private final Map<GtfsObjectType, Map<String, GtfsScopeState>> scopeByEntityId =
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
  private final Map<GtfsParseIssue, Map<String, Map<GtfsScopeState, LongAdder>>> issuesBySubTypeAndScope =
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
   * Everything the feed holds, this standing in only for the flat listing the base class offers. The summary places
   * each issue at the respect that filtered its entities and measures it against whatever reached that respect, so
   * the denominator belongs to the placement rather than to the issue
   * </p>
   */
  @Override
  protected long getDenominator(final GtfsParseIssue issue) {
    return getSeenInFeed(issue.getEntityType());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDenominatorLabel(final GtfsParseIssue issue) {
    return "feed";
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
      final GtfsObjectType entityType, final String subType, final GtfsScopeState state) {
    return seenByEntityTypeSubTypeAndScope
        .computeIfAbsent(entityType, type -> new ConcurrentHashMap<>())
        .computeIfAbsent(subType != null ? subType : NO_SUBTYPE, type -> new ConcurrentHashMap<>())
        .computeIfAbsent(state, type -> new LongAdder());
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
   * @return state, null where nothing was settled for it
   */
  public GtfsScopeState getSettledState(final GtfsObjectType entityType, final String entityId) {
    var index = scopeByEntityId.get(entityType);
    return index == null || entityId == null ? null : index.get(entityId);
  }

  /**
   * Register that an entity was ruled out in the given respect, a filter having excluded it.
   * <p>
   * Ruling out is absorbing: a trip already excluded for running on another day is not brought back by passing a
   * later test, so unlike the spatial respect, where parts accumulate into a whole, the respects a run is narrowed by
   * only ever narrow further
   * </p>
   *
   * @param entityType encountered
   * @param dimension the entity was ruled out in
   * @param entityId identifying the entity
   */
  public void registerSeenOutOfScope(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension, final String entityId) {
    settleScope(entityType, dimension, entityId, GtfsEntityScope.OUT);
  }

  /**
   * Register that an entity passed what a run is narrowed by in the given respect, leaving it ruled out where an
   * earlier filter already excluded it
   *
   * @param entityType encountered
   * @param dimension the entity passed in
   * @param entityId identifying the entity
   */
  public void registerSeenWithinScope(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension, final String entityId) {
    settleScope(entityType, dimension, entityId, GtfsEntityScope.IN);
  }

  /**
   * Settle the scope of an entity in a respect, moving it between cells of the tally so it stays counted exactly once
   *
   * @param entityType encountered
   * @param dimension to settle
   * @param entityId identifying the entity
   * @param scope it settles at, ignored where the entity is already ruled out in this respect
   */
  private void settleScope(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension, final String entityId,
      final GtfsEntityScope scope) {
    var index = scopeByEntityId.get(entityType);
    if (index == null || entityId == null) {
      return;
    }
    if (!dimension.appliesTo(entityType)) {
      throw new IllegalArgumentException(String.format(
          "GTFS %s scope does not apply to %s, unable to register it", dimension, entityType));
    }

    var knownState = index.getOrDefault(entityId, GtfsScopeState.unsettledFor(entityType));
    if (knownState.get(dimension) == GtfsEntityScope.OUT || knownState.get(dimension) == scope) {
      return;
    }

    var settledState = knownState.with(dimension, scope);
    var subType = getSeenSubType(entityType, entityId);
    var previousCounter = collectSeenCounter(entityType, subType, knownState);
    if (previousCounter.sum() <= 0) {
      return;
    }

    previousCounter.decrement();
    collectSeenCounter(entityType, subType, settledState).increment();
    index.put(entityId, settledState);
  }

  /**
   * Collect the scope settled for an individual entity in the given respect
   *
   * @param entityType to collect for
   * @param dimension to collect for
   * @param entityId to collect for
   * @return scope, null where nothing was settled for it
   */
  public GtfsEntityScope getSettledScope(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension, final String entityId) {
    var state = getSettledState(entityType, entityId);
    return state == null ? null : state.get(dimension);
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
    collectSeenCounter(
        entityType, subType != null ? subType.name() : null, GtfsScopeState.unsettledFor(entityType)).add(count);
  }

  /**
   * Register that an entity of the given type was encountered within a subType and with its scope already settled, as
   * is the case where what the entity is and where it sits are both known the moment it is read
   *
   * @param entityType encountered
   * @param subType within the entity type, may be null
   * @param scope of the entity relative to the area the run covers
   */
  public void registerSeen(
      final GtfsObjectType entityType, final Enum<?> subType, final GtfsScopeDimension dimension,
      final GtfsEntityScope scope) {
    collectSeenCounter(
        entityType, subType != null ? subType.name() : null,
        GtfsScopeState.unsettledFor(entityType).with(dimension, scope)).increment();
  }

  /**
   * Register that an entity of the given type was encountered within a subType and standing as given, for an entity
   * whose respects are all answered the moment it is read and which is therefore not indexed individually
   *
   * @param entityType encountered
   * @param subType within the entity type, may be null
   * @param state the entity stands in
   */
  public void registerSeen(
      final GtfsObjectType entityType, final Enum<?> subType, final GtfsScopeState state) {
    collectSeenCounter(entityType, subType != null ? subType.name() : null, state).increment();
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
      final GtfsObjectType entityType, final GtfsScopeDimension dimension, final String entityId,
      final GtfsEntityScope partScope) {
    var index = scopeByEntityId.get(entityType);
    if (index == null || entityId == null || partScope == null) {
      return;
    }
    if (!dimension.appliesTo(entityType)) {
      /* the respect cannot be asked of this kind of entity, so recording it would state something that has no
       * meaning rather than something not yet known */
      throw new IllegalArgumentException(String.format(
          "GTFS %s scope does not apply to %s, unable to register it", dimension, entityType));
    }

    var knownState = index.getOrDefault(entityId, GtfsScopeState.unsettledFor(entityType));
    var knownScope = knownState.get(dimension);
    var settledScope = !knownScope.isEstablished()
        ? partScope
        : (knownScope == partScope ? knownScope : GtfsEntityScope.PARTIAL);
    if (settledScope == knownScope) {
      return;
    }

    var settledState = knownState.with(dimension, settledScope);
    var subType = getSeenSubType(entityType, entityId);
    var previousCounter = collectSeenCounter(entityType, subType, knownState);
    if (previousCounter.sum() <= 0) {
      /* the entity was never counted as encountered, so moving it would put the tally into deficit and misreport
       * every share measured against it. Left where it is, the mismatch surfacing as an unsettled scope instead */
      return;
    }

    previousCounter.decrement();
    collectSeenCounter(entityType, subType, settledState).increment();
    index.put(entityId, settledState);
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
   * @param state the entity stood in when the issue arose, null to recover whatever was settled for it
   * @param entityId the GTFS id of the entity concerned, may be null when not entity specific
   * @param detailArgs the arguments the issue's detail template expects
   */
  public void registerIssue(
      final GtfsParseIssue issue, final Enum<?> subType, final GtfsScopeState state, final String entityId,
      final Object... detailArgs) {
    /* the scope the entity stood in as this arose, stated by the call site where the entity is not indexed
     * individually and recovered otherwise */
    var occurrenceState = getSettledState(issue.getEntityType(), entityId);
    if (occurrenceState == null) {
      occurrenceState = GtfsScopeState.unsettledFor(issue.getEntityType());
    }
    if (state != null) {
      /* stated by the call site, the entity not being indexed individually */
      occurrenceState = state;
    }


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
        .computeIfAbsent(occurrenceState, type -> new LongAdder()).increment();
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
      final GtfsObjectType entityType, final String subType, final GtfsScopeDimension dimension,
      final GtfsEntityScope scope) {
    return sumSeen(
        entityType, subType, state -> state.get(dimension) == scope);
  }

  /**
   * Total the entities of a type and, where given, a subType, whose state satisfies the given condition
   *
   * @param entityType to total for
   * @param subType to total for, null to total across every subType
   * @param condition the state is to satisfy
   * @return number encountered
   */
  private long sumSeen(
      final GtfsObjectType entityType, final String subType,
      final java.util.function.Predicate<GtfsScopeState> condition) {
    var subTypeCounters = seenByEntityTypeSubTypeAndScope.get(entityType);
    if (subTypeCounters == null) {
      return 0;
    }
    return subTypeCounters.entrySet().stream()
        .filter(entry -> subType == null || entry.getKey().equals(subType))
        .mapToLong(entry -> entry.getValue().entrySet().stream()
            .filter(scoped -> condition.test(scoped.getKey()))
            .mapToLong(scoped -> scoped.getValue().sum()).sum()).sum();
  }

  /**
   * Collect how many entities of a type were encountered with the given scope
   *
   * @param entityType to collect for
   * @param scope to collect for
   * @return number encountered
   */
  public long getSeenInScope(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension, final GtfsEntityScope scope) {
    return getSeenInScope(entityType, null, dimension, scope);
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
    return sumSeen(entityType, subType, state -> true);
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
  public long getSeenWithScope(final GtfsObjectType entityType, final GtfsScopeDimension dimension) {
    return getSeenWithScope(entityType, null, dimension);
  }

  /**
   * Collect how many entities of a type and, where given, a subType, had their scope settled at all
   *
   * @param entityType to collect for
   * @param subType to collect for, null to total across every subType
   * @return number with a settled scope
   */
  public long getSeenWithScope(
      final GtfsObjectType entityType, final String subType, final GtfsScopeDimension dimension) {
    return sumSeen(entityType, subType, state -> state.get(dimension).isEstablished());
  }

  /**
   * Collect how many entities of a type were ever within the area of the run, i.e. wholly or partly within the area it
   * covers. This is the denominator anything the parser achieved should be measured against
   *
   * @param entityType to collect for
   * @return number within the area
   */
  public long getSeenWithinScope(final GtfsObjectType entityType, final GtfsScopeDimension dimension) {
    return getSeenWithinScope(entityType, null, dimension);
  }

  /**
   * Collect how many entities of a type were within scope in the spatial respect
   *
   * @param entityType to collect for
   * @return number within the area
   */
  public long getSeenWithinArea(final GtfsObjectType entityType) {
    return getSeenWithinScope(entityType, null, GtfsScopeDimension.SPATIAL);
  }

  /**
   * Collect how many entities of a type and subType were within scope in the spatial respect
   *
   * @param entityType to collect for
   * @param subType to collect for
   * @return number within the area
   */
  public long getSeenWithinArea(final GtfsObjectType entityType, final String subType) {
    return getSeenWithinScope(entityType, subType, GtfsScopeDimension.SPATIAL);
  }

  /**
   * Collect how many entities of a type and, where given, a subType, were ever within the area of the run
   *
   * @param entityType to collect for
   * @param subType to collect for, null to total across every subType
   * @return number within the area
   */
  public long getSeenWithinScope(
      final GtfsObjectType entityType, final String subType, final GtfsScopeDimension dimension) {
    return sumSeen(entityType, subType, state -> state.get(dimension).isWithinArea());
  }

  /**
   * Collect how often each issue of an entity type was registered against entities standing at the given respect,
   * i.e. those the respect is the first to have ruled out, or those within scope throughout where none is given
   *
   * @param entityType to collect for
   * @param dimension to collect for, null for the entities within scope throughout
   * @return occurrences per issue, ordered by occurrences descending
   */
  private Map<GtfsParseIssue, Long> collectIssuesReportedAt(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension) {
    var byIssue = new LinkedHashMap<GtfsParseIssue, Long>();
    /* silently counted issues are included so that what a respect filtered still adds up, even where the entities it
     * concerns are deliberately never named. Leaving them out reported a respect's own discards as entities parsed
     * regardless of it */
    Arrays.stream(GtfsParseIssue.values())
        .filter(issue -> issue.getEntityType() == entityType && getOccurrences(issue) > 0)
        .map(issue -> Map.entry(issue, getOccurrencesReportedAt(issue, dimension)))
        .filter(entry -> entry.getValue() > 0)
        .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
        .forEach(entry -> byIssue.put(entry.getKey(), entry.getValue()));
    return byIssue;
  }

  /**
   * Collect how often an issue was registered against entities standing at the given respect, i.e. those the respect
   * is the first to have filtered, or those within scope throughout where none is given.
   * <p>
   * An occurrence stands at exactly one respect, so totalling this across the respects and the entities within scope
   * yields the occurrences of the issue and nothing besides
   * </p>
   *
   * @param issue to total for
   * @param dimension concerned, null for the entities within scope throughout
   * @return occurrences
   */
  public long getOccurrencesReportedAt(final GtfsParseIssue issue, final GtfsScopeDimension dimension) {
    var subTypeCounters = issuesBySubTypeAndScope.get(issue);
    if (subTypeCounters == null) {
      return 0;
    }
    return subTypeCounters.values().stream().mapToLong(
        scopeCounters -> scopeCounters.entrySet().stream().filter(
            entry -> getReportedRespectOf(issue.getEntityType(), entry.getKey()) == dimension).mapToLong(
            entry -> entry.getValue().sum()).sum()).sum();
  }

  /**
   * Total the occurrences of an issue within a subType against entities standing at the given respect
   *
   * @param issue to total for
   * @param subType to total for
   * @param dimension concerned, null for the entities within scope throughout
   * @return occurrences
   */
  private long countIssueReportedAt(
      final GtfsParseIssue issue, final String subType, final GtfsScopeDimension dimension) {
    var subTypeCounters = issuesBySubTypeAndScope.get(issue);
    if (subTypeCounters == null) {
      return 0;
    }
    var scopeCounters = subTypeCounters.get(subType);
    return scopeCounters == null ? 0 : scopeCounters.entrySet().stream().filter(
        entry -> getReportedRespectOf(issue.getEntityType(), entry.getKey()) == dimension).mapToLong(
        entry -> entry.getValue().sum()).sum();
  }

  /**
   * Report the issues standing at a respect, each broken down by the subTypes it arose within where that says
   * anything beyond the total
   *
   * @param entityType concerned
   * @param dimension concerned, null for the entities within scope throughout
   * @param denominator the occurrences are a share of
   * @param denominatorLabel naming what the denominator holds
   * @param depth to report at
   */
  private long logIssuesReportedAt(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension, final long denominator,
      final String denominatorLabel, final int depth) {
    var accountedFor = new LongAdder();
    collectIssuesReportedAt(entityType, dimension).forEach((issue, occurrences) -> {
      accountedFor.add(occurrences);
      if (!isReportedInSummary(issue)) {
        /* counted towards what the respect filtered, but never named: listing the stops of another region tells
         * nobody anything, which is the whole point of counting them silently */
        return;
      }
      LOGGER.info(LoggingUtils.settingsValue(
          issue.getDescription(),
          LoggingUtils.countWithPercentage(occurrences, denominator, denominatorLabel)
              + " [" + issue.getDisposition() + "]",
          depth));

      var subTypes = getSubTypesOf(issue).stream().filter(subType -> !NO_SUBTYPE.equals(subType)).collect(
          Collectors.toList());
      if (subTypes.size() < 2) {
        /* a single subType only restates the entry above it */
        return;
      }
      subTypes.stream()
          .map(subType -> Map.entry(subType, countIssueReportedAt(issue, subType, dimension)))
          .filter(entry -> entry.getValue() > 0)
          .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
          .forEach(entry -> LOGGER.info(LoggingUtils.settingsValue(
              entry.getKey(), String.valueOf(entry.getValue()), depth + 1)));
    });
    return accountedFor.sum();
  }

  /**
   * Collect how many entities of a type reached the given respect, i.e. how many were still within scope by the time
   * the respect before it had been applied. The first respect is reached by everything the feed holds
   *
   * @param entityType to collect for
   * @param dimension to collect for
   * @return number reaching the respect
   */
  public long getSeenReaching(final GtfsObjectType entityType, final GtfsScopeDimension dimension) {
    GtfsScopeDimension preceding = null;
    for (var candidate : getSettledRespectsOf(entityType)) {
      if (candidate == dimension) {
        break;
      }
      preceding = candidate;
    }
    return preceding == null ? getSeenInFeed(entityType) : getSeenWithinScopeUpTo(entityType, preceding);
  }

  /**
   * Collect the respects of an entity type that were settled for it at all, in the order they settle.
   * <p>
   * A respect the run never established says nothing about any entity of the type, so it neither places an entity nor
   * keeps one out of scope. Stops are the case in point: their modal respect is never settled, and treating it as a
   * failure would leave no stop in scope at all
   * </p>
   *
   * @param entityType to collect for
   * @return respects settled for it
   */
  public List<GtfsScopeDimension> getSettledRespectsOf(final GtfsObjectType entityType) {
    return GtfsScopeDimension.getApplicableTo(entityType).stream().filter(
        dimension -> hasScope(entityType, dimension)).collect(Collectors.toList());
  }

  /**
   * Collect the respect an entity standing as given is to be reported under, i.e. the first it was ruled out in.
   * <p>
   * Where it was ruled out in none it stands within scope throughout and belongs after the respects rather than at
   * one of them. Where it was ruled out in none but never reached one either, it belongs at the respect it never
   * reached, that being as far as it got
   * </p>
   *
   * @param entityType concerned
   * @param state the entity stands in
   * @return respect to report it under, null when it stands within scope throughout
   */
  public GtfsScopeDimension getReportedRespectOf(
      final GtfsObjectType entityType, final GtfsScopeState state) {
    GtfsScopeDimension unsettled = null;
    for (var dimension : getSettledRespectsOf(entityType)) {
      var scope = state.get(dimension);
      if (scope == GtfsEntityScope.OUT) {
        return dimension;
      }
      if (!scope.isEstablished() && unsettled == null) {
        unsettled = dimension;
      }
    }
    return unsettled;
  }

  /**
   * Collect the respects of an entity type up to and including the given one, in the order they settle
   *
   * @param entityType to collect for
   * @param dimension to collect up to
   * @return respects passed by the time this one is reached, the respect itself included
   */
  private static List<GtfsScopeDimension> collectRespectsUpTo(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension) {
    var upTo = new ArrayList<GtfsScopeDimension>();
    for (var candidate : GtfsScopeDimension.getApplicableTo(entityType)) {
      upTo.add(candidate);
      if (candidate == dimension) {
        break;
      }
    }
    return upTo;
  }

  /**
   * Collect how many entities of a type were within scope in the given respect and in every respect settled before it.
   * <p>
   * Cumulative rather than taken in isolation, a respect only ever being applied to whatever survived the respects
   * before it. Counting it alone reports entities already ruled out, which on a feed covering more ground than the
   * network yields a share of several thousand percent
   * </p>
   *
   * @param entityType to collect for
   * @param dimension to collect up to
   * @return number still within scope once this respect had been applied
   */
  public long getSeenWithinScopeUpTo(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension) {
    var upTo = collectRespectsUpTo(entityType, dimension);
    return sumSeen(
        entityType, null, state -> upTo.stream().allMatch(respect -> state.get(respect).isWithinArea()));
  }

  /**
   * Collect how the entities reaching a respect stand in it, i.e. those still within scope in every respect settled
   * before it, counted by the scope this respect settled them at
   *
   * @param entityType to collect for
   * @param dimension to collect for
   * @return number per scope, in the order the scopes are declared
   */
  public Map<GtfsEntityScope, Long> getSeenReachingByScope(
      final GtfsObjectType entityType, final GtfsScopeDimension dimension) {
    var preceding = collectRespectsUpTo(entityType, dimension);
    preceding.remove(dimension);

    var byScope = new LinkedHashMap<GtfsEntityScope, Long>();
    for (var scope : GtfsEntityScope.values()) {
      long count = sumSeen(
          entityType, null,
          state -> state.get(dimension) == scope
              && preceding.stream().allMatch(respect -> state.get(respect).isWithinArea()));
      if (count > 0) {
        byScope.put(scope, count);
      }
    }
    return byScope;
  }

  /**
   * Verify whether the scope of entities of a type was established at all. Where it was not, every entity of that type
   * has to be treated as within the area, there being nothing to say otherwise
   *
   * @param entityType to verify for
   * @return true when scope was recorded, false otherwise
   */
  public boolean hasScope(final GtfsObjectType entityType, final GtfsScopeDimension dimension) {
    return getSeenWithScope(entityType, dimension) > 0;
  }

  /**
   * Verify whether the spatial scope of entities of a type was established at all
   *
   * @param entityType to verify for
   * @return true when scope was recorded, false otherwise
   */
  public boolean hasScope(final GtfsObjectType entityType) {
    return hasScope(entityType, GtfsScopeDimension.SPATIAL);
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
      final GtfsParseIssue issue, final String subType, final GtfsScopeDimension dimension,
      final GtfsEntityScope scope) {
    var subTypeCounters = issuesBySubTypeAndScope.get(issue);
    if (subTypeCounters == null || subType == null) {
      return 0;
    }
    var scopeCounters = subTypeCounters.get(subType);
    if (scopeCounters == null) {
      return 0;
    }
    return scopeCounters.entrySet().stream()
        .filter(entry -> entry.getKey().get(dimension) == scope)
        .mapToLong(entry -> entry.getValue().sum()).sum();
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
          subType -> getOccurrences(issue, subType, GtfsScopeDimension.SPATIAL, scope)).sum();
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
    LOGGER.info(LoggingUtils.surroundWithBrackets("SCOPE") + "of the feed relative to what the run covers");
    for (var entityType : GtfsObjectType.values()) {
      long seen = getSeenInFeed(entityType);
      if (seen == 0) {
        continue;
      }

      /* the same shape the issue entries take, an entity label and what is being said about it, so the two blocks
       * read as one report rather than two */
      var label = String.format("%s | %ss", entityType.name().toLowerCase(), entityType.name().toLowerCase());
      LOGGER.info(LoggingUtils.settingsValue(label + " in feed", String.valueOf(seen), 1));

      /* one entry per respect, in the order they settle, each a share of whatever reached it rather than of the feed.
       * A share of the feed flattens the chain: it cannot tell a gate that costs little from one that costs almost
       * everything, which is the only thing worth knowing about a funnel */
      long reaching = seen;
      var reachingLabel = "feed";
      for (var dimension : GtfsScopeDimension.getApplicableTo(entityType)) {
        long within = getSeenWithinScopeUpTo(entityType, dimension);
        if (!hasScope(entityType, dimension) || within == reaching) {
          /* the respect filtered nothing, either because it was never settled or because it judged nothing out, and
           * a line saying so is noise in a funnel. Left out of the chain as well as off the page, an unsettled
           * respect holding nothing that the next respect's share should be measured against. What went unsettled
           * remains visible in the written tally */
          continue;
        }
        long filtered = reaching - within;
        /* both sides stated: what came through, out of what, and how many the respect filtered. The entries beneath
         * account for the filtered ones, so a heading counting only survivors leaves them measured against a number
         * they have nothing to do with */
        var value = new StringBuilder(String.format(
            "%d of %d reaching (%.2f%%), %d filtered",
            within, reaching, reaching > 0 ? (100.0 * within) / reaching : 0.0, filtered));

        /* only where being partly within is a distinction worth drawing, the filtered count already saying how many
         * fell outside altogether */
        var reachingByScope = getSeenReachingByScope(entityType, dimension);
        if (reachingByScope.getOrDefault(GtfsEntityScope.PARTIAL, 0L) > 0) {
          value.append("  [").append(reachingByScope.entrySet().stream().filter(
              entry -> entry.getKey().isWithinArea()).map(
              entry -> String.format("%s %d", entry.getKey().name(), entry.getValue())).collect(
              Collectors.joining(", "))).append("]");
        }
        LOGGER.info(LoggingUtils.settingsValue(label + " within " + dimension.getReportedName(), value.toString(), 1));

        /* what became of the entities this respect filtered, their shares summing to the whole of it */
        long accountedFor = logIssuesReportedAt(entityType, dimension, filtered, "filtered", 2);
        if (filtered > accountedFor) {
          /* a respect judges an entity out whether or not the run acts on it: without a configured bounding area
           * scope is measured and not parsed by, so these went into the result regardless. Stated rather than left
           * as the difference between two numbers further apart on the page */
          LOGGER.info(LoggingUtils.settingsValue(
              "filtered but parsed regardless",
              LoggingUtils.countWithPercentage(filtered - accountedFor, filtered, "filtered"), 2));
        }

        reaching = within;
        reachingLabel = dimension.getReportedName();
      }

      /* and what was lost among the entities that came through every respect, which is what the parser itself cost */
      if (reaching > 0 && !getSettledRespectsOf(entityType).isEmpty()) {
        LOGGER.info(LoggingUtils.settingsValue(
            label + " within scope", String.valueOf(reaching), 1));
        logIssuesReportedAt(entityType, null, reaching, "within scope", 2);
      }
    }
  }

  /**
   * Log what was within the area, then the discards grouped by stage, followed by the issues carried by entities that were
   * parsed regardless. Each line states its share of the entities of that type that were within the area, so a count is read
   * against what it could have been rather than in isolation
   */
  public void logSummary() {
    logScopeSummary();

    /* an entity type narrowed in no respect the run established has no funnel to report its losses under, so they are
     * gathered here rather than left unsaid */
    var unscopedTypes = Arrays.stream(GtfsObjectType.values()).filter(
        entityType -> getSeenInFeed(entityType) > 0 && getSettledRespectsOf(entityType).isEmpty()).collect(
        Collectors.toList());
    if (!unscopedTypes.isEmpty()) {
      LOGGER.info(LoggingUtils.surroundWithBrackets("ISSUES") + "of entities whose scope was never established");
      unscopedTypes.forEach(
          entityType -> logIssuesReportedAt(entityType, null, getSeenInFeed(entityType), "feed", 1));
    }
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
        var subTypeCounters = issuesBySubTypeAndScope.get(issue);
        for (var subType : getSubTypesOf(issue)) {
          var scopeCounters = subTypeCounters.get(subType);
          if (scopeCounters == null) {
            continue;
          }
          new TreeMap<String, GtfsScopeState>(){{
            scopeCounters.keySet().forEach(state -> put(state.toString(), state));
          }}.values().forEach(state -> {
            long occurrences = scopeCounters.get(state).sum();
            if (occurrences > 0) {
              writeIssueSummaryRow(
                  csvWriter, issue, NO_SUBTYPE.equals(subType) ? null : subType, state, occurrences, outcome);
            }
          });
        }
      }
    }
  }

  /**
   * Write a single row of the issue summary. Neither a total nor a share is written: both follow from where the
   * entity stood, which the row already carries, and from the coverage tally
   *
   * @param csvWriter to write with
   * @param issue the row reports on
   * @param subType the row reports on, null where the entity type is not subdivided
   * @param scope the entities stood in when the issue arose
   * @param occurrences of the issue within the subtype and scope
   * @param outcome what became of the entities it arose for
   */
  private void writeIssueSummaryRow(
      final SimpleCsvWriter csvWriter, final GtfsParseIssue issue, final String subType, final GtfsScopeState state,
      final long occurrences, final GtfsParseOutcome outcome) {
    csvWriter.writeRow(
        issue.getStage(),
        issue.getEntityType(),
        subType,
        state.get(GtfsScopeDimension.SPATIAL),
        state.get(GtfsScopeDimension.TEMPORAL),
        state.get(GtfsScopeDimension.MODAL),
        state.get(GtfsScopeDimension.SELECTION),
        issue.name(),
        issue.getDisposition(),
        outcome.getLabel(),
        occurrences);
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
          var scopeCounters = subTypeCounters.get(subType);
          new TreeMap<String, GtfsScopeState>(){{
            scopeCounters.keySet().forEach(state -> put(state.toString(), state));
          }}.values().forEach(state -> {
            long count = scopeCounters.get(state).sum();
            if (count > 0) {
              csvWriter.writeRow(
                  entityType,
                  NO_SUBTYPE.equals(subType) ? null : subType,
                  state.get(GtfsScopeDimension.SPATIAL),
                  state.get(GtfsScopeDimension.TEMPORAL),
                  state.get(GtfsScopeDimension.MODAL),
                  state.get(GtfsScopeDimension.SELECTION),
                  count);
            }
          });
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
