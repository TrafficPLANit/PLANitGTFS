package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.utils.csv.SimpleCsvWriter;
import org.goplanit.utils.misc.LogCollator;
import org.goplanit.utils.misc.LoggingUtils;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * What recording issues amounts to, irrespective of the kind of entity they concern.
 * <p>
 * Collating an occurrence, composing its context only where it will be read, sampling the entities it concerned,
 * giving it a line in a summary and writing it to a listing are the same work whether the entity came from the feed or
 * was one the converter set out to build from it. That work lives here, expressed against {@link GtfsIssue}, so that
 * what the two kinds of issue genuinely differ in is all that has to be stated separately.
 * </p>
 * <p>
 * Safe for concurrent use, which the integration stage requires since it maps leg segments from worker threads.
 * </p>
 *
 * @param <I> type of issue recorded
 *
 * @author markr
 */
public abstract class GtfsDiagnosticsBase<I extends Enum<I> & GtfsIssue> {

  /** The logger for this class */
  private static final Logger LOGGER = Logger.getLogger(GtfsDiagnosticsBase.class.getCanonicalName());

  /** upper bound on entity ids retained per issue for reporting, keeping totals exact regardless */
  public static final int DEFAULT_MAX_RETAINED_PER_ISSUE = 10_000;

  /** shared empty arguments, so registering an issue that takes no context allocates nothing */
  protected static final Object[] NO_DETAIL_ARGS = new Object[0];

  /** retention limit in force, so an empty instance can be created configured the same way */
  private final int maxRetainedPerIssue;

  /** sample size in force, so an empty instance can be created configured the same way */
  private final int logSampleSizeOfRetained;

  /**
   * Constructor
   *
   * @param maxRetainedPerIssue upper bound on entity ids retained per issue for reporting
   * @param logSampleSizeOfRetained how many of the retained entity ids each reported issue lists
   */
  protected GtfsDiagnosticsBase(final int maxRetainedPerIssue, final int logSampleSizeOfRetained) {
    this.maxRetainedPerIssue = maxRetainedPerIssue;
    this.logSampleSizeOfRetained = logSampleSizeOfRetained;
  }

  /**
   * Apply the configured sample size to the collators. To be called by an implementation once it has created them,
   * which it can only do after this base has been constructed
   */
  protected void applyLogSampleSizeOfRetained() {
    getCollators().forEach(collator -> collator.setLogSampleSizeOfRetained(logSampleSizeOfRetained));
  }

  /**
   * Collect how many of the retained entity ids each reported issue lists
   *
   * @return sample size
   */
  public int getLogSampleSizeOfRetained() {
    return logSampleSizeOfRetained;
  }

  /**
   * Collect the collator an issue's occurrences are held by, allowing an implementation to separate its issues by
   * what they cost the entity
   *
   * @param issue to collect for
   * @return collator holding its occurrences
   */
  protected abstract LogCollator collatorFor(I issue);

  /**
   * Collect every collator in use, so that what applies to all of them need not name them individually
   *
   * @return collators
   */
  protected abstract Collection<LogCollator> getCollators();

  /**
   * Collect the issue going by the given name, which an enum knows how to do but a generic type cannot
   *
   * @param name of the issue
   * @return the issue
   */
  protected abstract I issueValueOf(String name);

  /**
   * Collect what the denominator of an issue holds, so a share states the population it was measured against rather
   * than leaving it to be assumed
   *
   * @param issue to collect for
   * @return label naming the denominator
   */
  protected String getDenominatorLabel(final I issue) {
    return "total";
  }

  /**
   * Collect what an issue's occurrences are to be reported as a share of, i.e. the entities it could conceivably have
   * arisen for
   *
   * @param issue to collect for
   * @return denominator, zero when the issue has none and its count stands on its own
   */
  protected abstract long getDenominator(I issue);

  /**
   * Write a single recorded occurrence to a listing, the columns of which are the implementation's to decide
   *
   * @param csvWriter to write with
   * @param issue the occurrence belongs to
   * @param occurrence to write
   * @param outcome what became of the entities the file covers
   */
  protected abstract void writeIssueRow(
      SimpleCsvWriter csvWriter, I issue, LogCollator.Occurrence occurrence, GtfsParseOutcome outcome);

  /**
   * Collect the upper bound on entity ids retained per issue
   *
   * @return retention limit in force
   */
  public int getMaxRetainedPerIssue() {
    return maxRetainedPerIssue;
  }

  /**
   * Record a single occurrence of an issue, composing whatever context accompanies it and reporting it at once where
   * the issue asks to be heard immediately
   *
   * @param issue encountered
   * @param entityId identifying the entity concerned, may be null when not entity specific
   * @param subType subdividing the entity type, may be null when the type is not subdivided
   * @param detailArgs the arguments the issue's detail template expects
   */
  protected void registerIssueOccurrence(
      final I issue, final String entityId, final String subType, final Object... detailArgs) {
    if (issue == null) {
      throw new IllegalArgumentException("issue is required to register a GTFS diagnostic");
    }

    var collator = collatorFor(issue);

    String detail = null;
    if (issue.getLogPolicy() == GtfsIssueLogPolicy.SILENT_COUNT_ONLY) {
      /* nothing about the individual case is ever read, so the entity is accounted for and nothing else is kept */
      collator.incrementCountOnly(issue.name());
    } else {
      /* the context in its logged form reaches the log for an issue that speaks the moment it arises and for the
       * handful of occurrences the summary samples, and in its expanded form only a listing, so neither is composed
       * for an occurrence that will not be read that way. The occurrence itself is counted regardless */
      String expandedDetail = null;
      if (issue.hasDetailTemplate()
          && (issue.getLogPolicy() == GtfsIssueLogPolicy.IMMEDIATE || collator.isWithinLogSample(issue.name()))) {
        detail = issue.createDetail(detailArgs);
      }
      if (issue.hasPersistedDetailTemplate() && collator.isRetaining(issue.name())) {
        expandedDetail = issue.createPersistedDetail(detailArgs);
      }
      collator.increment(issue.name(), entityId, subType, detail, expandedDetail);
    }

    if (issue.getLogPolicy() == GtfsIssueLogPolicy.IMMEDIATE) {
      LOGGER.severe(createIssueMessage(issue, entityId, detail));
    }
  }

  /**
   * Compose the message reporting a single occurrence of an issue, i.e. what was lost or degraded, which entity it
   * concerned and whatever context the call site supplied
   *
   * @param issue to report
   * @param entityId concerned, may be null
   * @param detail supplied, may be null
   * @return created message
   */
  protected static String createIssueMessage(
      final GtfsIssue issue, final String entityId, final String detail) {
    var message = new StringBuilder(
        issue.isDiscarding() ? "DISCARD: " : "").append(issue.getDescription());
    if (entityId != null) {
      message.append(String.format(" (%s %s)", issue.getEntityLabel(), entityId));
    }
    if (detail != null) {
      message.append(", ").append(detail);
    }
    return message.toString();
  }

  /**
   * Collect how often an issue was registered
   *
   * @param issue to collect for
   * @return number of occurrences
   */
  public long getOccurrences(final I issue) {
    return collatorFor(issue).getOccurrences(issue.name());
  }

  /**
   * Verify whether an issue occurred and is to be given a line of its own in the summary. A silent issue is counted
   * and persisted like any other, it simply does not speak, since what it records is already accounted for elsewhere
   *
   * @param issue to verify
   * @return true when it is to be reported, false otherwise
   */
  protected boolean isReportedInSummary(final I issue) {
    return issue.getLogPolicy() != GtfsIssueLogPolicy.SILENT_COUNT_ONLY && getOccurrences(issue) > 0;
  }

  /**
   * Create the log entry for a single issue, i.e. its description, its count with the share of the entities it could
   * have arisen for, and a sample of the entity ids concerned
   *
   * @param issue to report
   * @return created log entry
   */
  /**
   * Create what a reported issue says about the parser, which by default is the single disposition it is declared
   * with. An implementation whose occurrences do not share one states the split instead
   *
   * @param issue to report
   * @return created label
   */
  protected String createDispositionLabel(final I issue) {
    return issue.getDisposition().name();
  }

  protected String createIssueLogEntry(final I issue) {
    var collator = collatorFor(issue);
    var template = collator.getTemplate(issue.name());
    var label = String.format("%s | %s", issue.getEntityLabel(), issue.getDescription());
    var value = new StringBuilder(LoggingUtils.countWithPercentage(
        getOccurrences(issue), getDenominator(issue), getDenominatorLabel(issue)));
    value.append(" [").append(createDispositionLabel(issue)).append("]");

    if (template != null) {
      /* context accompanies the sampled entities where it was composed, so the line states what kind of case this is
       * rather than only how many there were */
      var samples = template.getRetainedOccurrences().stream().filter(
          LogCollator.Occurrence::hasEntityId).limit(collator.getLogSampleSizeOfRetained()).map(
          occurrence -> occurrence.hasDetail()
              ? String.format("%s (%s)", occurrence.getEntityId(), occurrence.getDetail())
              : occurrence.getEntityId()).collect(Collectors.toList());
      if (!samples.isEmpty()) {
        value.append("   e.g. ");
        if (issue.hasEntityIdLabel()) {
          /* what the sampled ids denote is stated once rather than against each of them, which is needed only where
           * they are not the ids of the entity the issue is reported under */
          value.append(issue.getEntityIdLabel()).append(" ");
        }
        value.append(String.join(", ", samples));
        if (getOccurrences(issue) > samples.size()) {
          value.append(", ...");
        }
      }
    }
    return LoggingUtils.settingsValue(label, value.toString(), 2);
  }

  /**
   * Collect how often an issue was registered per subtype of its entity type, ordered by subtype
   *
   * @param issue to collect for
   * @return occurrences per entity subtype, empty when the entity type is not subdivided
   */
  protected SortedMap<String, Long> getOccurrencesBySubType(final I issue) {
    return Collections.emptySortedMap();
  }

  /**
   * Collect how often an issue was registered against entities of each scope, ordered by scope
   *
   * @param issue to collect for
   * @return occurrences per scope, empty where scope was never established for its entity type
   */
  protected Map<String, Long> getOccurrencesByScope(final I issue) {
    return Collections.emptyMap();
  }

  /**
   * Report an issue as a single entry, followed by how it splits across the subtypes of its entity type where it is
   * subdivided, so a total can be read against the kinds of entity making it up rather than only in isolation
   *
   * @param issue to report
   */
  protected void logIssue(final I issue) {
    LOGGER.info(createIssueLogEntry(issue));

    /* only where the issue spans more than one scope, that being the case a total on its own misrepresents: a count
     * dominated by entities beyond the area says more about the ground the feed covers than about the parser */
    var byScope = getOccurrencesByScope(issue);
    if (byScope.size() > 1) {
      LOGGER.info(LoggingUtils.settingsValue(
          "scope distribution",
          byScope.entrySet().stream().map(
              entry -> String.format("%s %d", entry.getKey(), entry.getValue())).collect(
              Collectors.joining(", ")),
          3));
    }

    getOccurrencesBySubType(issue).forEach(
        (subType, occurrences) -> LOGGER.info(LoggingUtils.settingsValue(
            subType,
            LoggingUtils.countWithPercentage(
                occurrences, getDenominator(issue, subType), getDenominatorLabel(issue)),
            3)));
  }

  /**
   * Collect what an issue's occurrences within a subtype are to be reported as a share of, i.e. the entities of that
   * subtype it could conceivably have arisen for. Measured against the same population as the issue as a whole, so a
   * breakdown and the entry above it are read on the same footing
   *
   * @param issue to collect for
   * @param subType to collect for
   * @return denominator, zero when there is none and the count stands on its own
   */
  protected long getDenominator(final I issue, final String subType) {
    return getDenominator(issue);
  }

  /**
   * Persist one row per recorded occurrence held by the given collator
   *
   * @param filePath to write to
   * @param collator holding the occurrences
   * @param headers of the file, in column order
   * @param outcome what became of the entities the file covers
   */
  protected void persistEntityIssues(
      final Path filePath, final LogCollator collator, final String[] headers, final GtfsParseOutcome outcome) {
    try (var csvWriter = SimpleCsvWriter.create(filePath, headers)) {
      for (var template : collator.getTemplatesByOccurrencesDescending()) {
        var issue = issueValueOf(template.getTemplateId());
        for (var occurrence : template.getRetainedOccurrences()) {
          writeIssueRow(csvWriter, issue, occurrence, outcome);
        }
        if (template.hasUnretainedOccurrences() && issue.getLogPolicy() != GtfsIssueLogPolicy.SILENT_COUNT_ONLY) {
          /* the rows are a sample rather than the record, so say so here instead of letting the row count be read
           * as the total. A silently counted issue carries no detail by design, so its unretained occurrences are
           * not a shortfall to report */
          LOGGER.warning(String.format(
              "%s lists %d of %d %s occurrences, raise the retention limit to list them all",
              filePath.getFileName(), template.getRetainedOccurrences().size(), template.getOccurrences(),
              issue.name()));
        }
      }
    }
  }

  /**
   * Discard everything recorded so far
   */
  public void reset() {
    getCollators().forEach(LogCollator::reset);
  }

  /**
   * Collect the counter for a key, creating it only when genuinely absent. Registering an entity happens millions of
   * times on a sizeable feed, so the common path is a plain lookup rather than a compute
   *
   * @param <K> type of key
   * @param counters to collect from
   * @param key to collect for
   * @return counter to add to
   */
  protected static <K> LongAdder collectCounter(final Map<K, LongAdder> counters, final K key) {
    var adder = counters.get(key);
    if (adder == null) {
      adder = counters.computeIfAbsent(key, absentKey -> new LongAdder());
    }
    return adder;
  }

  /**
   * Collect the per subtype counters for a key, creating them only when genuinely absent
   *
   * @param <K> type of key
   * @param countersByKey to collect from
   * @param key to collect for
   * @return per subtype counters
   */
  protected static <K> Map<String, LongAdder> collectSubTypeCounters(
      final Map<K, Map<String, LongAdder>> countersByKey, final K key) {
    var counters = countersByKey.get(key);
    if (counters == null) {
      counters = countersByKey.computeIfAbsent(key, absentKey -> new ConcurrentHashMap<>());
    }
    return counters;
  }

  /**
   * Express the counters as totals ordered by name, so what is reported is stable between runs
   *
   * @param counters to express, may be null
   * @return totals by name
   */
  protected static SortedMap<String, Long> asSortedTotals(final Map<String, LongAdder> counters) {
    if (counters == null) {
      return Collections.emptySortedMap();
    }
    var totals = new TreeMap<String, Long>();
    counters.forEach((name, adder) -> totals.put(name, adder.sum()));
    return totals;
  }
}
