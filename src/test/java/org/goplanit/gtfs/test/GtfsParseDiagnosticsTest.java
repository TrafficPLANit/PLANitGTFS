package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.diagnostics.GtfsCoverageCsvColumn;
import org.goplanit.gtfs.converter.diagnostics.GtfsEntityScope;
import org.goplanit.gtfs.converter.diagnostics.GtfsScopeDimension;
import org.goplanit.gtfs.converter.diagnostics.GtfsScopeState;
import org.goplanit.gtfs.converter.diagnostics.GtfsIssueSummaryCsvColumn;
import org.goplanit.gtfs.converter.diagnostics.GtfsIssueDisposition;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseIssue;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseStage;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the tracking of what became of GTFS entities during a parse
 *
 * @author markr
 */
public class GtfsParseDiagnosticsTest {

  @TempDir
  Path tempDir;

  @Test
  public void seenParsedAndDiscardedReconcileTest() {
    var diagnostics = GtfsParseDiagnostics.create();

    diagnostics.registerSeen(GtfsObjectType.ROUTE, 100);
    diagnostics.registerIssue(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS, "r1");
    diagnostics.registerIssue(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS, "r2");
    diagnostics.registerIssue(GtfsParseIssue.ROUTE_NO_SERVICES_LAYER_FOR_MODE, "r3", "bus", RouteType.BUS_SERVICE);

    assertEquals(100, diagnostics.getSeenInFeed(GtfsObjectType.ROUTE));
    assertEquals(3, diagnostics.getDiscarded(GtfsObjectType.ROUTE));
    assertEquals(97, diagnostics.getParsed(GtfsObjectType.ROUTE));

    /* the split is what separates deliberate filtering from a capability gap */
    assertEquals(2, diagnostics.getDiscarded(GtfsObjectType.ROUTE, GtfsIssueDisposition.BY_DESIGN));
    assertEquals(1, diagnostics.getDiscarded(GtfsObjectType.ROUTE, GtfsIssueDisposition.LIMITATION));
    assertEquals(0, diagnostics.getDiscarded(GtfsObjectType.ROUTE, GtfsIssueDisposition.PROBLEM));
  }

  @Test
  public void retainedIssueDoesNotDiscardTest() {
    var diagnostics = GtfsParseDiagnostics.create();

    diagnostics.registerSeen(GtfsObjectType.STOP, 10);
    diagnostics.registerIssue(
        GtfsParseIssue.STOP_POSSIBLY_ON_WRONG_SIDE_OF_ROAD, "s1", "Central Station", "151.2", "-33.8",
        "id: 7, xmlId: l_7, extId: 71", "Eddy Avenue", "id: 9, xmlId: l_9, extId: 91");
    diagnostics.registerIssue(
        GtfsParseIssue.STOP_PREFERRED_ACCESS_LINK_SEGMENT_NOT_ADJACENT, "s1", "Central Station", "151.2", "-33.8",
        "id: 12, xmlId: tz_12, extId: 9981", "Central Platform 3", "id: 44, xmlId: ls_44, extId: 77");

    /* the stop carries two issues yet was still parsed, so it must not count against the parsed total */
    assertEquals(10, diagnostics.getParsed(GtfsObjectType.STOP));
    assertEquals(0, diagnostics.getDiscarded(GtfsObjectType.STOP));
    assertEquals(2, diagnostics.getRetainedIssues(GtfsObjectType.STOP));
    assertFalse(diagnostics.isDiscarded(GtfsObjectType.STOP, "s1"));
  }

  @Test
  public void suppressionIndexTest() {
    var diagnostics = GtfsParseDiagnostics.create();

    diagnostics.registerIssue(GtfsParseIssue.TRIP_OUTSIDE_TIME_PERIOD, "t1");

    assertTrue(diagnostics.isDiscarded(GtfsObjectType.TRIP, "t1"));
    assertEquals(GtfsParseIssue.TRIP_OUTSIDE_TIME_PERIOD, diagnostics.getDiscardIssue(GtfsObjectType.TRIP, "t1"));
    assertFalse(diagnostics.isDiscarded(GtfsObjectType.TRIP, "t2"));
    assertNull(diagnostics.getDiscardIssue(GtfsObjectType.TRIP, "t2"));
  }

  @Test
  public void stopTimesCountedButNotIndexedTest() {
    var diagnostics = GtfsParseDiagnostics.create();

    diagnostics.registerIssue(GtfsParseIssue.STOP_TIME_OF_DISCARDED_TRIP, "st1");

    /* counted so the total reconciles, not indexed since a large feed holds millions of them */
    assertEquals(1, diagnostics.getDiscarded(GtfsObjectType.STOP_TIME));
    assertFalse(diagnostics.isDiscarded(GtfsObjectType.STOP_TIME, "st1"));
  }

  @Test
  public void parsedNeverNegativeTest() {
    var diagnostics = GtfsParseDiagnostics.create();

    /* discards registered without a matching seen total must not produce a negative parsed count */
    diagnostics.registerIssue(GtfsParseIssue.TRIP_WITHOUT_LEGS, "t1");

    assertEquals(0, diagnostics.getParsed(GtfsObjectType.TRIP));
  }

  @Test
  public void resetTest() {
    var diagnostics = GtfsParseDiagnostics.create();

    diagnostics.registerSeen(GtfsObjectType.TRIP, 5);
    diagnostics.registerIssue(GtfsParseIssue.TRIP_WITHOUT_LEGS, "t1");
    diagnostics.reset();

    assertEquals(0, diagnostics.getSeenInFeed(GtfsObjectType.TRIP));
    assertEquals(0, diagnostics.getDiscarded(GtfsObjectType.TRIP));
    assertFalse(diagnostics.isDiscarded(GtfsObjectType.TRIP, "t1"));
  }

  @Test
  public void everyIssueIsSelfConsistentTest() {
    /* an issue has to name a stage, an entity type and a disposition for the report to place it in a column */
    Arrays.stream(GtfsParseIssue.values()).forEach(issue -> {
      assertNotNull(issue.getStage());
      assertNotNull(issue.getEntityType());
      assertNotNull(issue.getDisposition());
      assertTrue(issue.getDescription() != null && !issue.getDescription().isBlank());
    });

    /* every stage must contribute issues, otherwise a stage reports nothing and looks clean by omission */
    Arrays.stream(GtfsParseStage.values()).forEach(
        stage -> assertFalse(GtfsParseIssue.getIssuesForStage(stage).isEmpty()));
  }

  @Test
  public void categoryBreakdownTest() {
    var diagnostics = GtfsParseDiagnostics.create();

    diagnostics.registerSeen(GtfsObjectType.ROUTE, RouteType.BUS_SERVICE, 10);
    diagnostics.registerSeen(GtfsObjectType.ROUTE, RouteType.FERRY, 4);
    diagnostics.registerIssue(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS, RouteType.BUS_SERVICE, "r1");
    diagnostics.registerIssue(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS, RouteType.BUS_SERVICE, "r2");

    /* the aggregate is maintained independently, so it holds regardless of which call sites supply a category */
    assertEquals(14, diagnostics.getSeenInFeed(GtfsObjectType.ROUTE));
    assertEquals(2, diagnostics.getDiscarded(GtfsObjectType.ROUTE));
    assertEquals(12, diagnostics.getParsed(GtfsObjectType.ROUTE));

    assertEquals(10, diagnostics.getSeenInFeed(GtfsObjectType.ROUTE, RouteType.BUS_SERVICE));
    assertEquals(4, diagnostics.getSeenInFeed(GtfsObjectType.ROUTE, RouteType.FERRY));
    assertEquals(
        2, diagnostics.getDiscarded(GtfsObjectType.ROUTE, RouteType.BUS_SERVICE.name(), null));
    assertEquals(0, diagnostics.getDiscarded(GtfsObjectType.ROUTE, RouteType.FERRY.name(), null));
    assertEquals(8, diagnostics.getParsed(GtfsObjectType.ROUTE, RouteType.BUS_SERVICE.name()));

    /* ordered by category so what is reported does not shuffle between runs */
    assertEquals(
        List.of(RouteType.BUS_SERVICE.name(), RouteType.FERRY.name()),
        List.copyOf(diagnostics.getSeenBySubType(GtfsObjectType.ROUTE).keySet()));
  }

  @Test
  public void countsOnlyRetainsNoEntitiesTest() {
    var diagnostics = GtfsParseDiagnostics.createCountsOnly();

    diagnostics.registerSeen(GtfsObjectType.TRIP, 5);
    diagnostics.registerIssue(GtfsParseIssue.TRIP_WITHOUT_LEGS, "t1");

    /* totals stay exact while nothing is retained for listing */
    assertEquals(5, diagnostics.getSeenInFeed(GtfsObjectType.TRIP));
    assertEquals(1, diagnostics.getDiscarded(GtfsObjectType.TRIP));

    /* the suppression index is functional rather than diagnostic, so it keeps its ids regardless of the mode */
    assertTrue(diagnostics.isDiscarded(GtfsObjectType.TRIP, "t1"));
  }

  @Test
  public void newEmptyInstanceLeavesOriginalIntactTest() {
    var diagnostics = GtfsParseDiagnostics.create();
    diagnostics.registerSeen(GtfsObjectType.TRIP, 5);
    diagnostics.registerIssue(GtfsParseIssue.TRIP_WITHOUT_LEGS, "t1");

    var replacement = diagnostics.newEmptyInstance();

    /* whoever collected the original keeps what it holds */
    assertEquals(5, diagnostics.getSeenInFeed(GtfsObjectType.TRIP));
    assertEquals(1, diagnostics.getDiscarded(GtfsObjectType.TRIP));
    assertEquals(0, replacement.getSeenInFeed(GtfsObjectType.TRIP));
    assertEquals(0, replacement.getDiscarded(GtfsObjectType.TRIP));
  }

  @Test
  public void mergeOfDisjointEntityTypesTest() {
    /* mirrors the real split: the services stage records routes and trips, the stop stage records stops */
    var services = GtfsParseDiagnostics.create();
    services.registerSeen(GtfsObjectType.ROUTE, RouteType.BUS_SERVICE, 10);
    services.registerSeen(GtfsObjectType.TRIP, 100);
    services.registerIssue(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS, RouteType.BUS_SERVICE, "r1");
    services.registerIssue(GtfsParseIssue.TRIP_WITHOUT_LEGS, "t1");

    var stops = GtfsParseDiagnostics.create();
    stops.registerSeen(GtfsObjectType.STOP, 40);
    stops.registerIssue(GtfsParseIssue.STOP_OUTSIDE_BOUNDING_AREA, "s1", "Central Station", "151.2", "-33.8");

    services.merge(stops);

    assertEquals(10, services.getSeenInFeed(GtfsObjectType.ROUTE));
    assertEquals(100, services.getSeenInFeed(GtfsObjectType.TRIP));
    assertEquals(40, services.getSeenInFeed(GtfsObjectType.STOP));
    assertEquals(1, services.getOccurrences(GtfsParseIssue.STOP_OUTSIDE_BOUNDING_AREA));
    assertEquals(1, services.getOccurrences(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS));
    assertEquals(1, services.getOccurrences(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS, RouteType.BUS_SERVICE.name()));
    assertEquals(39, services.getParsed(GtfsObjectType.STOP));

    /* the suppression index must survive the merge, since later stages test membership against it */
    assertTrue(services.isDiscarded(GtfsObjectType.ROUTE, "r1"));
    assertTrue(services.isDiscarded(GtfsObjectType.TRIP, "t1"));
  }

  @Test
  public void mergeRejectsOverlappingEntityTypeTest() {
    var first = GtfsParseDiagnostics.create();
    first.registerSeen(GtfsObjectType.STOP, 10);

    var second = GtfsParseDiagnostics.create();
    second.registerSeen(GtfsObjectType.STOP, 5);

    var thrown = assertThrows(PlanItRunTimeException.class, () -> first.merge(second));
    assertTrue(thrown.getMessage().contains(GtfsObjectType.STOP.name()));

    /* a rejected merge must leave the totals as they were rather than partially applied */
    assertEquals(10, first.getSeenInFeed(GtfsObjectType.STOP));
  }

  @Test
  public void mergeRejectsOverlapIntroducedByIssuesOnlyTest() {
    /* the overlap is on stops even though only one side ever counted a stop as seen */
    var first = GtfsParseDiagnostics.create();
    first.registerSeen(GtfsObjectType.STOP, 10);

    var second = GtfsParseDiagnostics.create();
    second.registerIssue(GtfsParseIssue.STOP_OUTSIDE_BOUNDING_AREA, "s1", "Central Station", "151.2", "-33.8");

    assertThrows(PlanItRunTimeException.class, () -> first.merge(second));
  }

  @Test
  public void mergeAllowsOverlapWhenExplicitlyPermittedTest() {
    var first = GtfsParseDiagnostics.create();
    first.registerSeen(GtfsObjectType.STOP, 10);

    var second = GtfsParseDiagnostics.create();
    second.registerSeen(GtfsObjectType.STOP, 5);

    first.merge(second, true);
    assertEquals(15, first.getSeenInFeed(GtfsObjectType.STOP));
  }

  @Test
  public void mergeOfNullIsHarmlessTest() {
    var diagnostics = GtfsParseDiagnostics.create();
    diagnostics.registerSeen(GtfsObjectType.STOP, 10);
    diagnostics.merge(null);
    assertEquals(10, diagnostics.getSeenInFeed(GtfsObjectType.STOP));
  }

  @Test
  public void persistTest() throws IOException {
    var diagnostics = GtfsParseDiagnostics.create();

    diagnostics.registerSeen(GtfsObjectType.ROUTE, 10);
    diagnostics.registerSeen(GtfsObjectType.STOP, 4);
    diagnostics.registerSeen(GtfsObjectType.STOP_TIME, 20);
    diagnostics.registerIssue(GtfsParseIssue.STOP_TIME_DUPLICATE, "t1", "s9", "4");
    diagnostics.registerIssue(
        GtfsParseIssue.STOP_POSSIBLY_ON_WRONG_SIDE_OF_ROAD, "s1", "Central Station", "151.2", "-33.8",
        "id: 7, xmlId: l_7, extId: 71", "Eddy Avenue", "id: 9, xmlId: l_9, extId: 91");

    diagnostics.persist(tempDir, true);

    var discards = Files.readAllLines(tempDir.resolve("gtfs_discards.csv"), StandardCharsets.UTF_8);
    var issues = Files.readAllLines(tempDir.resolve("gtfs_issues.csv"), StandardCharsets.UTF_8);
    var summary = Files.readAllLines(tempDir.resolve("gtfs_coverage_summary.csv"), StandardCharsets.UTF_8);

    assertEquals(2, discards.size());
    assertTrue(discards.get(1).contains("STOP_TIME_DUPLICATE"));
    assertTrue(discards.get(1).contains("t1"));
    /* the detail is composed by the issue from the arguments supplied, rather than written out at the call site */
    assertTrue(discards.get(1).contains("stop s9 at sequence position 4"));

    assertEquals(2, issues.size());
    assertTrue(issues.get(1).contains("STOP_POSSIBLY_ON_WRONG_SIDE_OF_ROAD"));
    /* the persisted form names and locates the stop even though the logged form of this issue states nothing */
    assertTrue(issues.get(1).contains("stop Central Station at (151.2, -33.8)"));

    /* one row per entity type that was seen or lost, so an absent type is absent rather than reported as zero */
    assertEquals(4, summary.size());
  }

  @Test
  public void persistLeavesOutByDesignIssuesTest() throws IOException {
    var diagnostics = GtfsParseDiagnostics.create();

    diagnostics.registerSeen(GtfsObjectType.ROUTE, 10);
    diagnostics.registerSeen(GtfsObjectType.STOP_TIME, 20);
    diagnostics.registerIssue(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS, "r1");
    diagnostics.registerIssue(GtfsParseIssue.STOP_TIME_DUPLICATE, "t1", "s9", "4");

    diagnostics.persist(tempDir, false);
    var discards = Files.readAllLines(tempDir.resolve("gtfs_discards.csv"), StandardCharsets.UTF_8);

    /* what the run was asked to leave out is not listed entity by entity, what it did not ask for still is */
    assertEquals(2, discards.size());
    assertTrue(discards.get(1).contains("STOP_TIME_DUPLICATE"));

    /* its total is reported either way, the listing being a detail behind the count rather than the count itself */
    assertEquals(1, diagnostics.getOccurrences(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS));
  }

  @Test
  public void persistedCoverageRowsAreDisjointTest() throws IOException {
    var diagnostics = GtfsParseDiagnostics.create();

    /* routes always carry a subtype, stop times never do, so both kinds of row have to appear */
    diagnostics.registerSeen(GtfsObjectType.ROUTE, RouteType.BUS_SERVICE, 10);
    diagnostics.registerSeen(GtfsObjectType.ROUTE, RouteType.FERRY, 4);
    diagnostics.registerIssue(GtfsParseIssue.ROUTE_EXCLUDED_BY_SETTINGS, RouteType.BUS_SERVICE, "r1");
    diagnostics.registerSeen(GtfsObjectType.STOP_TIME, 1000);
    diagnostics.registerIssue(GtfsParseIssue.STOP_TIME_DUPLICATE, "st1", "s1", "1");

    diagnostics.persist(tempDir, true);
    var rows = Files.readAllLines(tempDir.resolve("gtfs_coverage_summary.csv"), StandardCharsets.UTF_8);

    long countOfRoutes = 0;
    long countOfStopTimes = 0;
    int routeRows = 0;
    for (var row : rows.subList(1, rows.size())) {
      var values = row.split(",", -1);
      var entityType = values[GtfsCoverageCsvColumn.ENTITY_TYPE.ordinal()];
      var count = Long.parseLong(values[GtfsCoverageCsvColumn.COUNT.ordinal()]);
      if (GtfsObjectType.ROUTE.name().equals(entityType)) {
        ++routeRows;
        countOfRoutes += count;
        /* every route has a subtype, so no route row may be written without one */
        assertFalse(values[GtfsCoverageCsvColumn.SUBTYPE.ordinal()].isEmpty());
      } else if (GtfsObjectType.STOP_TIME.name().equals(entityType)) {
        countOfStopTimes += count;
        /* no stop time has a subtype, so its rows carry none */
        assertTrue(values[GtfsCoverageCsvColumn.SUBTYPE.ordinal()].isEmpty());
      }
    }

    /* every cell of the tally is its own row and the rows are disjoint, so they sum to what the feed holds without
     * any totals row being present to restate it */
    assertEquals(2, routeRows);
    assertEquals(diagnostics.getSeenInFeed(GtfsObjectType.ROUTE), countOfRoutes);
    assertEquals(diagnostics.getSeenInFeed(GtfsObjectType.STOP_TIME), countOfStopTimes);
  }

  @Test
  public void persistedIssueSummaryStatesWhereEntitiesStoodTest() throws IOException {
    var diagnostics = GtfsParseDiagnostics.create();

    /* a stop stands in both its respects the moment it is read, stated by the call site since stops are not indexed
     * individually */
    diagnostics.registerSeen(
        GtfsObjectType.STOP, null, GtfsScopeDimension.SPATIAL, GtfsEntityScope.IN);
    diagnostics.registerIssue(
        GtfsParseIssue.STOP_EXCLUDED_BY_SETTINGS, (Enum<?>) null,
        GtfsScopeState.unsettledFor(GtfsObjectType.STOP)
            .with(GtfsScopeDimension.SPATIAL, GtfsEntityScope.IN)
            .with(GtfsScopeDimension.SELECTION, GtfsEntityScope.OUT),
        "s1", "a stop", 1.0, 2.0);

    /* a trip ruled out on the day it runs, its other respects never reached */
    diagnostics.registerSeen(GtfsObjectType.TRIP, 5);
    diagnostics.registerSeenOutOfScope(GtfsObjectType.TRIP, GtfsScopeDimension.TEMPORAL, "t1");
    diagnostics.registerIssue(GtfsParseIssue.TRIP_SERVICE_ID_NOT_ACTIVE_ON_DAY, "t1");

    diagnostics.persist(tempDir, true);
    var rows = Files.readAllLines(tempDir.resolve("gtfs_issue_summary.csv"), StandardCharsets.UTF_8);

    var scopesByIssue = new java.util.HashMap<String, String[]>();
    for (var row : rows.subList(1, rows.size())) {
      var values = row.split(",", -1);
      scopesByIssue.put(values[GtfsIssueSummaryCsvColumn.ISSUE.ordinal()], values);
    }

    /* a stop left out by name stands within the area and beyond the exclusions, which is what places the issue */
    var excludedStop = scopesByIssue.get(GtfsParseIssue.STOP_EXCLUDED_BY_SETTINGS.name());
    assertEquals(
        GtfsEntityScope.IN.name(), excludedStop[GtfsIssueSummaryCsvColumn.SPATIAL_SCOPE.ordinal()]);
    assertEquals(
        GtfsEntityScope.OUT.name(), excludedStop[GtfsIssueSummaryCsvColumn.SELECTION_SCOPE.ordinal()]);

    /* a trip on another day stands beyond the run in time, and nowhere else it ever reached */
    var inactiveTrip = scopesByIssue.get(GtfsParseIssue.TRIP_SERVICE_ID_NOT_ACTIVE_ON_DAY.name());
    assertEquals(
        GtfsEntityScope.OUT.name(), inactiveTrip[GtfsIssueSummaryCsvColumn.TEMPORAL_SCOPE.ordinal()]);
    assertEquals(
        GtfsEntityScope.NOT_ESTABLISHED.name(),
        inactiveTrip[GtfsIssueSummaryCsvColumn.SPATIAL_SCOPE.ordinal()]);
  }
}


