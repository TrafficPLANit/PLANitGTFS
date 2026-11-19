package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.diagnostics.GtfsParseDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseIssue;
import org.goplanit.gtfs.converter.diagnostics.GtfsScopeDimension;
import org.goplanit.gtfs.enums.GtfsObjectType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What must hold of a parse report whatever feed produced it.
 * <p>
 * The figures the report carries are its product, and a wrong one is far harder to notice than a failure: a share
 * beyond 100% or a respect that appears to let more through than reached it reads as a number rather than as a fault.
 * These say what cannot be true, so such a figure fails a test rather than waiting to be read.
 * </p>
 *
 * @author markr
 */
public final class GtfsDiagnosticsAssertions {

  /**
   * Assert everything that must hold of the given report
   *
   * @param diagnostics to verify
   */
  public static void assertConsistent(final GtfsParseDiagnostics diagnostics) {
    assertScopeNarrowsMonotonically(diagnostics);
    assertOccurrencesStandAtExactlyOneRespect(diagnostics);
    assertOccurrencesStandWhereTheirEntityWasTallied(diagnostics);
    assertRespectsAccountForWhatTheyRuledOut(diagnostics);
  }

  /**
   * Assert that every scope an issue was registered against is one its entity type was actually tallied under.
   * <p>
   * The tally and the issue register speak about the same entities, the one saying how many stood where and the other
   * why they were let go. Where an entity type is not indexed by id the two are established separately and can
   * disagree, a respect then stating how much it ruled out while the entities it ruled out stand somewhere that
   * respect never reports on
   * </p>
   *
   * @param diagnostics to verify
   */
  public static void assertOccurrencesStandWhereTheirEntityWasTallied(final GtfsParseDiagnostics diagnostics) {
    for (var issue : GtfsParseIssue.values()) {
      if (diagnostics.getOccurrences(issue) == 0) {
        continue;
      }
      var tallied = diagnostics.getSeenStates(issue.getEntityType());
      for (var state : diagnostics.getIssueStates(issue)) {
        assertTrue(
            tallied.contains(state),
            String.format(
                "%s stands at %s, a scope no %s was tallied under", issue, state, issue.getEntityType()));
      }
    }
  }

  /**
   * Assert that each respect lets through no more than reached it.
   * <p>
   * A respect is only ever applied to whatever survived those before it, so the counts can only fall. Taking a respect
   * in isolation instead reports entities already filtered and yields shares of several thousand percent
   * </p>
   *
   * @param diagnostics to verify
   */
  public static void assertScopeNarrowsMonotonically(final GtfsParseDiagnostics diagnostics) {
    for (var entityType : GtfsObjectType.values()) {
      long reaching = diagnostics.getSeenInFeed(entityType);
      for (var dimension : diagnostics.getSettledRespectsOf(entityType)) {
        long within = diagnostics.getSeenWithinScopeUpTo(entityType, dimension);
        assertTrue(
            within <= reaching,
            String.format(
                "%s within %s is %d, more than the %d that reached it", entityType, dimension, within, reaching));
        reaching = within;
      }
    }
  }

  /**
   * Assert that every occurrence of an issue stands at exactly one respect, or at none and so within scope throughout.
   * <p>
   * The report places an occurrence by where its entity stood. Were a placement to miss occurrences, or claim some
   * twice, the entries beneath a respect would no longer account for what it filtered
   * </p>
   *
   * @param diagnostics to verify
   */
  public static void assertOccurrencesStandAtExactlyOneRespect(final GtfsParseDiagnostics diagnostics) {
    for (var issue : GtfsParseIssue.values()) {
      long occurrences = diagnostics.getOccurrences(issue);
      if (occurrences == 0) {
        continue;
      }

      var respects = new ArrayList<GtfsScopeDimension>(
          diagnostics.getSettledRespectsOf(issue.getEntityType()));
      respects.add(null);

      long placed = respects.stream().mapToLong(
          dimension -> diagnostics.getOccurrencesReportedAt(issue, dimension)).sum();
      assertEquals(
          occurrences, placed,
          String.format("%s has %d occurrences but %d were placed", issue, occurrences, placed));
    }
  }

  /**
   * Assert that each respect accounts for exactly what it ruled out, neither leaving an entity without a reason nor
   * claiming more than it took away.
   * <p>
   * A respect that rules out more than its entries name reports how much it cost without ever saying why, which is
   * the one thing a reader cannot recover from the report itself. That the reason is known is not enough: it has to
   * have been recorded against the entity, at the respect that decided it, or the two accounts drift apart
   * </p>
   *
   * @param diagnostics to verify
   */
  public static void assertRespectsAccountForWhatTheyRuledOut(final GtfsParseDiagnostics diagnostics) {
    for (var entityType : GtfsObjectType.values()) {
      for (var dimension : diagnostics.getSettledRespectsOf(entityType)) {
        /* what faced the respect rather than what survived the one before it, an entity lost in scope between the two
         * never having faced this one */
        long ruledOut =
            diagnostics.getSeenFacing(entityType, dimension)
                - diagnostics.getSeenWithinScopeUpTo(entityType, dimension);

        /* those the respect was the first to put out, rather than those merely standing at it, an entity whose scope
         * was never established standing there without the respect having ruled on it at all */
        long accountedFor = 0;
        for (var issue : GtfsParseIssue.values()) {
          if (issue.getEntityType() == entityType) {
            accountedFor += diagnostics.getOccurrencesFilteredAt(issue, dimension);
          }
        }
        assertEquals(
            ruledOut, accountedFor,
            String.format(
                "%s at %s ruled out %d entities while its entries account for %d",
                entityType, dimension, ruledOut, accountedFor));
      }
    }
  }

  /**
   * Assert that a written report matches the one recorded against it, line for line.
   * <p>
   * Reserved for the summaries, which hold a row per counted cell. The per occurrence listings are capped by the
   * retention limit and are therefore a sample rather than a record, so comparing them would fail for reasons that
   * say nothing about correctness
   * </p>
   *
   * @param outputDirectory the report was written to
   * @param referenceDirectory holding what it is expected to say
   * @param fileName to compare
   * @throws IOException when either cannot be read
   */
  public static void assertSummaryFilesSimilar(
      final String outputDirectory, final String referenceDirectory, final String fileName) throws IOException {
    var produced = Files.readAllLines(Path.of(outputDirectory, fileName), StandardCharsets.UTF_8);
    var expected = Files.readAllLines(Path.of(referenceDirectory, fileName), StandardCharsets.UTF_8);

    for (int index = 0; index < Math.min(produced.size(), expected.size()); ++index) {
      assertEquals(
          expected.get(index), produced.get(index),
          String.format("%s differs on line %d", fileName, index + 1));
    }
    assertEquals(expected.size(), produced.size(), String.format("%s has a different number of rows", fileName));
  }
}
