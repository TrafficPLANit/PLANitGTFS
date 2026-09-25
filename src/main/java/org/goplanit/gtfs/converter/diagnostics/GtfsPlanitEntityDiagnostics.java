package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.utils.csv.SimpleCsvWriter;
import org.goplanit.utils.misc.LogCollator;
import org.goplanit.utils.misc.LoggingUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  /** subdivision an entity is counted under where it belongs to none */
  private static final String NO_SUBTYPE = "";

  /** listing the recorded occurrences are written to */
  private static final String ISSUES_FILE_NAME = "gtfs_planit_entity_issues.csv";

  /** entities of each type that were already in the result before the converter added anything to it */
  private final Map<GtfsPlanitEntityType, LongAdder> preExistingByType = new ConcurrentHashMap<>();

  /** how many entities of the type nested under hold how many entities of a type, by type */
  private final Map<GtfsPlanitEntityType, SortedMap<Long, Long>> sharingByType = new ConcurrentHashMap<>();

  /** entities of each type, and of each subdivision of it, the converter set out to build */
  private final Map<GtfsPlanitEntityType, Map<String, LongAdder>> desiredByType = new ConcurrentHashMap<>();

  /** entities of each type, and of each subdivision of it, that came about */
  private final Map<GtfsPlanitEntityType, Map<String, LongAdder>> createdByType = new ConcurrentHashMap<>();

  /**
   * Entities of each type, and of each subdivision of it, lost building the PLANit result rather than through the
   * feed side losing what they were to be built from.
   * <p>
   * Counted alongside the issues that name them because an issue is not recorded per subdivision of its entity type,
   * so what a subdivision lost of its own accord cannot be read back off them
   * </p>
   */
  private final Map<GtfsPlanitEntityType, Map<String, LongAdder>> lostByType = new ConcurrentHashMap<>();

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
    return issue.isKnockOnFromGtfsParsing()
        ? getDesired(issue.getEntityType()) : getSurvivingKnockOn(issue.getEntityType());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDenominatorLabel(final GtfsPlanitEntityIssue issue) {
    return issue.isKnockOnFromGtfsParsing() ? super.getDenominatorLabel(issue) : "surviving knock-on";
  }

  /**
   * Collect the entities of a type that the losses carried over from the feed side left behind, which is what became
   * of them plus what was subsequently lost building the PLANit result.
   * <p>
   * The population an issue of PLANit's own making could have arisen for. Measured against everything that was to be
   * built, such an issue reads as negligible whenever the feed side took away most of it, which says how much of the
   * feed reached here rather than how much of what reached here was lost
   * </p>
   *
   * @param entityType to collect for
   * @return number left for the PLANit side to lose
   */
  private long getSurvivingKnockOn(final GtfsPlanitEntityType entityType) {
    long ownLosses = Arrays.stream(GtfsPlanitEntityIssue.values())
        .filter(issue -> issue.getEntityType() == entityType && !issue.isKnockOnFromGtfsParsing())
        .mapToLong(this::getOccurrences).sum();
    return getCreated(entityType) + ownLosses;
  }

  /**
   * Collect the entities within a subdivision of a type that the losses carried over from the feed side left behind
   *
   * @param entityType to collect for
   * @param subType to collect for
   * @return number left for the PLANit side to lose
   */
  private long getSurvivingKnockOn(final GtfsPlanitEntityType entityType, final String subType) {
    return getCreated(entityType, subType) + sumOf(lostByType.get(entityType), subType);
  }

  /**
   * State what is in the result alongside the account that arrives at it, being what was already there, what the
   * parse added to it, and what was taken away again once the result was brought in line with the physical network
   *
   * @param inResult that are in the result
   * @param preExisting that were there before the parse added anything
   * @param added by the parse
   * @return presence value
   */
  private static String createPresenceValue(
      final long inResult, final long preExisting, final long added) {
    if (preExisting == 0 && added == 0) {
      /* nothing was counted before or after, the entity being one that is established rather than accumulated, so
       * the count stands on its own */
      return String.valueOf(inResult);
    }
    return String.format(
        "%d (%d pre-existing, %d added, %d removed)",
        inResult, preExisting, added, preExisting + added - inResult);
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
   * Register entities of a type that were already in the result before the converter added anything to it
   *
   * @param entityType concerned
   * @param numberOfEntities present beforehand
   */
  public void registerPreExisting(final GtfsPlanitEntityType entityType, final long numberOfEntities) {
    preExistingByType.computeIfAbsent(entityType, absentType -> new LongAdder()).add(numberOfEntities);
  }

  /**
   * Collect how many entities of a type were already in the result before the converter added anything to it
   *
   * @param entityType to collect for
   * @return number present beforehand
   */
  public long getPreExisting(final GtfsPlanitEntityType entityType) {
    var counter = preExistingByType.get(entityType);
    return counter != null ? counter.sum() : 0;
  }

  /**
   * Register how the entities of a type divide over the entities of the type it is nested under, so that a count
   * exceeding the one it is reported beneath can be reconciled with it
   *
   * @param entityType concerned
   * @param entitiesPerNestingEntity entities of the type held by each entity of the type it is nested under, one
   *          entry per entity holding at least one
   */
  public void registerSharing(
      final GtfsPlanitEntityType entityType, final Collection<Long> entitiesPerNestingEntity) {
    var distribution = new TreeMap<Long, Long>();
    entitiesPerNestingEntity.forEach(entities -> distribution.merge(entities, 1L, Long::sum));
    sharingByType.put(entityType, distribution);
  }

  /**
   * Register entities of a type that were to be in the result, i.e. those the converter set out to build, or those it
   * had already built when a post-processing step that may remove them commenced
   *
   * @param entityType concerned
   * @param numberOfEntities that were to be in the result
   */
  public void registerDesired(final GtfsPlanitEntityType entityType, final long numberOfEntities) {
    registerDesired(entityType, null, numberOfEntities);
  }

  /**
   * Register entities of a type, within a subdivision of it, that were to be in the result
   *
   * @param entityType concerned
   * @param subType the entities belong to, null where the type is not subdivided
   * @param numberOfEntities that were to be in the result
   */
  public void registerDesired(
      final GtfsPlanitEntityType entityType, final String subType, final long numberOfEntities) {
    collectCounter(
        collectSubTypeCounters(desiredByType, entityType),
        subType != null ? subType : NO_SUBTYPE).add(numberOfEntities);
  }

  /**
   * Register entities of a type that are in the result, i.e. those that came about and were not removed again
   *
   * @param entityType concerned
   * @param numberOfEntities in the result
   */
  public void registerCreated(final GtfsPlanitEntityType entityType, final long numberOfEntities) {
    registerCreated(entityType, null, numberOfEntities);
  }

  /**
   * Register entities of a type, within a subdivision of it, that are in the result
   *
   * @param entityType concerned
   * @param subType the entities belong to, null where the type is not subdivided
   * @param numberOfEntities in the result
   */
  public void registerCreated(
      final GtfsPlanitEntityType entityType, final String subType, final long numberOfEntities) {
    collectCounter(
        collectSubTypeCounters(createdByType, entityType),
        subType != null ? subType : NO_SUBTYPE).add(numberOfEntities);
  }

  /**
   * Register entities of a type, within a subdivision of it, lost building the PLANit result rather than through the
   * feed side losing what they were to be built from
   *
   * @param entityType concerned
   * @param subType the entities belong to, null where the type is not subdivided
   * @param numberOfEntities lost
   */
  public void registerLost(
      final GtfsPlanitEntityType entityType, final String subType, final long numberOfEntities) {
    collectCounter(
        collectSubTypeCounters(lostByType, entityType),
        subType != null ? subType : NO_SUBTYPE).add(numberOfEntities);
  }

  /**
   * Collect the counters held per subdivision of an entity type, creating them where the type is new
   *
   * @param counters to collect from
   * @param entityType to collect for
   * @return counters by subdivision
   */
  private static Map<String, LongAdder> collectSubTypeCounters(
      final Map<GtfsPlanitEntityType, Map<String, LongAdder>> counters, final GtfsPlanitEntityType entityType) {
    return counters.computeIfAbsent(entityType, absentType -> new ConcurrentHashMap<>());
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
    return sumOf(desiredByType.get(entityType));
  }

  /**
   * Collect how many entities within a subdivision of a type the converter set out to build
   *
   * @param entityType to collect for
   * @param subType to collect for
   * @return number set out to build
   */
  public long getDesired(final GtfsPlanitEntityType entityType, final String subType) {
    return sumOf(desiredByType.get(entityType), subType);
  }

  /**
   * Collect the subdivisions an entity type was counted under, ordered by name
   *
   * @param entityType to collect for
   * @return subdivisions, empty where the type is not subdivided
   */
  public Set<String> getSubTypes(final GtfsPlanitEntityType entityType) {
    var subTypes = new TreeSet<String>();
    /* a subdivision an entity ended up in need not be one anything was expected of, how an entity came about being
     * settled as it comes about rather than in advance */
    Stream.of(desiredByType.get(entityType), createdByType.get(entityType))
        .filter(Objects::nonNull)
        .forEach(counters -> counters.keySet().stream()
            .filter(subType -> !NO_SUBTYPE.equals(subType))
            .forEach(subTypes::add));
    return subTypes;
  }

  /**
   * Total the counters held per subdivision
   *
   * @param counters to total, may be null
   * @return total
   */
  private static long sumOf(final Map<String, LongAdder> counters) {
    return counters == null ? 0 : counters.values().stream().mapToLong(LongAdder::sum).sum();
  }

  /**
   * Collect a single subdivision's counter
   *
   * @param counters to collect from, may be null
   * @param subType to collect for
   * @return count
   */
  private static long sumOf(final Map<String, LongAdder> counters, final String subType) {
    if (counters == null) {
      return 0;
    }
    var counter = counters.get(subType);
    return counter != null ? counter.sum() : 0;
  }

  /**
   * Collect how many entities of a type came about
   *
   * @param entityType to collect for
   * @return number created
   */
  public long getCreated(final GtfsPlanitEntityType entityType) {
    return sumOf(createdByType.get(entityType));
  }

  /**
   * Collect how many entities within a subdivision of a type came about
   *
   * @param entityType to collect for
   * @param subType to collect for
   * @return number created
   */
  public long getCreated(final GtfsPlanitEntityType entityType, final String subType) {
    return sumOf(createdByType.get(entityType), subType);
  }

  /**
   * Absorb what the given diagnostics recorded, so the stages that contributed to a parse can be reported as one
   *
   * @param other to absorb
   */
  public void merge(final GtfsPlanitEntityDiagnostics other) {
    other.preExistingByType.forEach((type, counter) -> preExistingByType.computeIfAbsent(
        type, absentType -> new LongAdder()).add(counter.sum()));
    other.desiredByType.forEach((type, counters) -> counters.forEach(
        (subType, counter) -> collectCounter(
            collectSubTypeCounters(desiredByType, type), subType).add(counter.sum())));
    other.createdByType.forEach((type, counters) -> counters.forEach(
        (subType, counter) -> collectCounter(
            collectSubTypeCounters(createdByType, type), subType).add(counter.sum())));
    other.lostByType.forEach((type, counters) -> counters.forEach(
        (subType, counter) -> collectCounter(
            collectSubTypeCounters(lostByType, type), subType).add(counter.sum())));
    sharingByType.putAll(other.sharingByType);
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

    var knockOn = new ArrayList<GtfsPlanitEntityIssue>();
    var ownLosses = new ArrayList<GtfsPlanitEntityIssue>();
    var modifications = new ArrayList<GtfsPlanitEntityIssue>();
    for (var issue : GtfsPlanitEntityIssue.values()) {
      if (!isReportedInSummary(issue)) {
        continue;
      }
      if (issue.isModification()) {
        /* the entity is still in the result, so whatever brought the change about it is not a shortfall to account
         * for and does not belong among the entities that are gone */
        modifications.add(issue);
      } else {
        (issue.isKnockOnFromGtfsParsing() ? knockOn : ownLosses).add(issue);
      }
    }

    /* what the feed cost, then what building the result changed and what it cost, then what that leaves, so that a
     * reader arrives at the totals having already seen what they are the remainder of */
    logKnockOnFromGtfsParsing(knockOn);
    logChangedBuildingResult(modifications);
    logLostBuildingResult(ownLosses);

    LOGGER.info(LoggingUtils.settingsValue(
        "Final result", "count (pre-existing, added by the parse, removed again)", 2));
    for (var component : GtfsPlanitComponent.values()) {
      var reportedTypes = Arrays.stream(GtfsPlanitEntityType.values())
          .filter(entityType -> entityType.getComponent() == component)
          .filter(entityType -> entityType.getNestedUnder() == null)
          .filter(entityType -> getDesired(entityType) > 0 || getCreated(entityType) > 0)
          .collect(Collectors.toList());
      if (reportedTypes.isEmpty()) {
        continue;
      }

      /* the component the entities end up in, so a reader can tell which part of the result an entry speaks of */
      LOGGER.info(LoggingUtils.settingsEntry(component.getLabel(), 3));
      reportedTypes.forEach(entityType -> logPresenceOf(entityType, 4));
    }
  }

  /**
   * Log the entities building the PLANit result kept but altered, largest first.
   * <p>
   * These are not a shortfall: the entity is in the result, shaped to what the network it had to be expressed on
   * allows. Stated among the losses they would read as entities gone, and the counts would appear to contradict what
   * the result holds
   * </p>
   *
   * @param issues to log, those that alter rather than remove
   */
  private void logChangedBuildingResult(final List<GtfsPlanitEntityIssue> issues) {
    if (issues.isEmpty()) {
      return;
    }

    LOGGER.info(LoggingUtils.settingsValue(
        "Changed building the PLANit result", "entity kept, altered to fit the result", 2));
    issues.stream()
        .sorted((left, right) -> Long.compare(getOccurrences(right), getOccurrences(left)))
        .forEach(issue -> {
          LOGGER.info(createIssueLogEntry(issue, 3));
          logSubTypesOf(issue);
        });
  }

  /**
   * Log what was lost building the PLANit result itself, the feed side accounting for none of it.
   * <p>
   * These stand apart from what the feed cost: they are what the converter did to entities that had made it this far,
   * and the only entries here a reader can act upon
   * </p>
   *
   * @param issues to log, those whose loss originates here
   */
  private void logLostBuildingResult(final List<GtfsPlanitEntityIssue> issues) {
    if (issues.isEmpty()) {
      return;
    }

    LOGGER.info(LoggingUtils.settingsValue(
        "Lost building the PLANit result", "not accounted for by the feed side", 2));
    issues.forEach(issue -> {
      LOGGER.info(createIssueLogEntry(issue, 3));
      logSubTypesOf(issue);
    });
  }

  /**
   * Log what was lost here because the GTFS entities behind it were lost, largest first.
   * <p>
   * How many were lost is worth stating, one discarded stop costing as many legs as it was a stop of, and that
   * multiplier appears nowhere on the feed side. Why they were lost does not: it is the reason already given there,
   * and repeating the split of it entry by entry says the same thing a second time in the same report. What is left is
   * the count, with the listing holding the cause against each entity for anyone who needs to follow one through
   * </p>
   *
   * @param issues to log, those whose loss carries over from the feed side
   */
  private void logKnockOnFromGtfsParsing(final List<GtfsPlanitEntityIssue> issues) {
    if (issues.isEmpty()) {
      return;
    }

    LOGGER.info(LoggingUtils.settingsValue(
        "Knock-on from GTFS parsing", "cause per entity in " + ISSUES_FILE_NAME, 2));
    issues.stream()
        .sorted((left, right) -> Long.compare(getOccurrences(right), getOccurrences(left)))
        .forEach(issue -> {
          LOGGER.info(LoggingUtils.settingsValue(
              String.format("%s | %s", issue.getEntityLabel(), issue.getDescription()),
              LoggingUtils.countWithPercentage(
                  getOccurrences(issue), getDenominator(issue), getDenominatorLabel(issue)), 3));
          logSubTypesWithoutGtfsCauseOf(issue);
        });
  }

  /**
   * Log the occurrences of a knock-on issue whose subdivision names no GTFS issue, the feed side accounting for
   * everything but these
   *
   * @param issue to log for
   */
  private void logSubTypesWithoutGtfsCauseOf(final GtfsPlanitEntityIssue issue) {
    getOccurrencesBySubType(issue).entrySet().stream()
        .filter(entry -> GtfsParseIssue.findByName(entry.getKey()) == null)
        .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
        .forEach(entry -> LOGGER.info(LoggingUtils.settingsValue(
            entry.getKey(),
            LoggingUtils.countWithPercentage(entry.getValue(), getOccurrences(issue), "occurrences")
                + " [" + getDispositionOf(issue, entry.getKey()) + "]", 4)));
  }

  /**
   * Log how an entity type divides over the subdivisions it was counted under, each stated as what it is in the result
   * against what was to be there, largest first.
   * <p>
   * A type that only ever falls short in part of what it covers reads as though it fell short throughout, the total
   * alone being unable to tell one mode running as it should from another that lost nearly everything
   * </p>
   *
   * @param entityType to log for
   */
  private void logPresenceOf(final GtfsPlanitEntityType entityType, final int indent) {
    LOGGER.info(LoggingUtils.settingsValue(
        entityType.getLabel(),
        appendSharing(
            entityType,
            createPresenceValue(
                getCreated(entityType), getPreExisting(entityType), getDesired(entityType))), indent));
    logSubTypesOf(entityType, indent + 1);

    /* a type subdividing this one counts the same entities from another angle, so it belongs underneath it rather
     * than beside it, where two counts that cannot agree read as though they should */
    Arrays.stream(GtfsPlanitEntityType.values())
        .filter(nestedType -> nestedType.getNestedUnder() == entityType)
        .filter(nestedType -> getDesired(nestedType) > 0 || getCreated(nestedType) > 0)
        .forEach(nestedType -> logPresenceOf(nestedType, indent + 1));
  }

  /**
   * Append how the entities of a type divide over the entities of the type it is nested under, where that was
   * established, so that a count exceeding the one above it is explained rather than left to read as a disagreement
   *
   * @param entityType to append for
   * @param presence to append to
   * @return presence, with the division appended where there is one
   */
  private String appendSharing(final GtfsPlanitEntityType entityType, final String presence) {
    var sharing = sharingByType.get(entityType);
    if (sharing == null || sharing.isEmpty()) {
      return presence;
    }

    var split = sharing.entrySet().stream()
        .map(entry -> String.format("%d with %d", entry.getValue(), entry.getKey()))
        .collect(Collectors.joining(", "));
    return String.format(
        "%s across %d %ss (%s)",
        presence, sharing.values().stream().mapToLong(Long::longValue).sum(),
        entityType.getNestedUnder().getLabel(), split);
  }

  /**
   * Log how an entity type divides over the subdivisions it was counted under, largest first
   *
   * @param entityType to log for
   * @param indent to log at
   */
  private void logSubTypesOf(final GtfsPlanitEntityType entityType, final int indent) {
    var subTypes = getSubTypes(entityType);
    if (subTypes.size() < 2 && subTypes.stream().noneMatch(subType -> GtfsPlanitEntityOrigin.findByName(subType) != null)) {
      /* a single subdivision only restates the entry above it, unless it says how the entities came about, which the
       * entry above cannot say */
      return;
    }

    subTypes.stream()
        .sorted((left, right) -> Long.compare(getCreated(entityType, right), getCreated(entityType, left)))
        .forEach(subType -> LOGGER.info(LoggingUtils.settingsValue(
            describeSubType(subType), describeSubTypePresence(entityType, subType), indent)));
  }

  /**
   * Describe what a subdivision of an entity type holds, which is what is in the result where that is counted for the
   * subdivision, and otherwise what the parse added within it, a subdivision naming how an entity came about saying
   * nothing about what later became of it
   *
   * @param entityType to describe for
   * @param subType to describe for
   * @return value
   */
  private String describeSubTypePresence(final GtfsPlanitEntityType entityType, final String subType) {
    long created = getCreated(entityType, subType);
    return created > 0
        ? createPresenceValue(created, 0, getDesired(entityType, subType))
        : String.valueOf(getDesired(entityType, subType));
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
                + " [" + getDispositionOf(issue, entry.getKey()) + "]", 4)));
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
   * @return description, the subtype itself where it names neither an issue nor a way an entity came about
   */
  private static String describeSubType(final String subType) {
    var originIssue = GtfsParseIssue.findByName(subType);
    if (originIssue != null) {
      return originIssue.getDescription();
    }
    var transferZoneOrigin = GtfsPlanitEntityOrigin.findByName(subType);
    return transferZoneOrigin != null ? transferZoneOrigin.getDescription() : subType;
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
   * @param persistByDesignIssues whether occurrences of what the run was asked to leave out are written as well
   */
  public void persist(final Path outputDirectory, final boolean persistByDesignIssues) {
    persistEntityIssues(
        outputDirectory.resolve(ISSUES_FILE_NAME), issues, GtfsPlanitEntityCsvColumn.getHeaders(), null,
        persistByDesignIssues);
  }

  /**
   * {@inheritDoc}
   * <p>
   * A derived entity is lost for reasons that differ in kind within a single issue, so an occurrence is weighed by
   * what its own subdivision earned rather than by what its issue is declared with
   * </p>
   */
  @Override
  protected GtfsIssueDisposition getDispositionOf(
      final GtfsPlanitEntityIssue issue, final LogCollator.Occurrence occurrence) {
    return getDispositionOf(issue, occurrence.getEntitySubType());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    super.reset();
    preExistingByType.clear();
    sharingByType.clear();
    desiredByType.clear();
    createdByType.clear();
    lostByType.clear();
    occurrencesBySubType.clear();
    dispositionBySubType.clear();
  }
}
