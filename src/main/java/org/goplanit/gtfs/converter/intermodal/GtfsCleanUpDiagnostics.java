package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.gtfs.converter.diagnostics.GtfsEntityScope;
import org.goplanit.gtfs.converter.diagnostics.GtfsIssueDisposition;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseIssue;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityIssue;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityType;
import org.goplanit.gtfs.converter.diagnostics.GtfsScopeDimension;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.ExternalIdAble;
import org.goplanit.utils.misc.CharacterUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.service.ServiceLegSegments;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.network.layer.service.ServiceNodes;
import org.goplanit.utils.service.routed.RelativeLegTimingUtils;
import org.goplanit.utils.service.routed.RoutedService;
import org.goplanit.utils.service.routed.RoutedTripDeparture;
import org.goplanit.utils.service.routed.RoutedTripSchedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Records what the clean-up steps that align the parsed services with the physical network take away again.
 * <p>
 * What each step removes is established by comparing the entities present before it ran with those present after,
 * rather than by the step reporting it itself, the steps residing in the PLANit core where GTFS has no place. The
 * entities are compared by identity because a removal is followed by a renumbering of what survives
 * </p>
 *
 * @author markr
 */
class GtfsCleanUpDiagnostics {

  /** subtype a removal is reported within when nothing is recorded of where the GTFS entities behind it stood */
  private static final String TRIP_NOT_RECORDED_SUBTYPE = "TRIP_NOT_RECORDED";

  /**
   * the scopes an entity can stand in relative to the area, furthest outside it first. Declared here rather than
   * taken from the order the scopes themselves are declared in, which reads as a scale but is not one
   */
  private static final List<GtfsEntityScope> SCOPES_FURTHEST_OUTSIDE_FIRST =
      List.of(GtfsEntityScope.OUT, GtfsEntityScope.PARTIAL, GtfsEntityScope.IN);

  /** subtype a removal is reported within when nothing is recorded of what befell the stops behind it */
  private static final String CAUSE_NOT_RECORDED_SUBTYPE = "CAUSE_NOT_RECORDED";

  /** to record what is removed into */
  private final GtfsPlanitEntityDiagnostics diagnostics;

  /**
   * What the truncation made of a trip schedule and what brought it about.
   * <p>
   * The two are held together because they are settled in one pass over the schedule's legs, and because what a
   * removal says about the parser follows from the cause rather than from the outcome
   * </p>
   */
  static class TruncationOutcome {

    /** issue the schedule is reported under */
    private final GtfsPlanitEntityIssue issue;

    /** what befell the stops whose legs went unmapped, null where nothing is recorded of them */
    private final GtfsParseIssue cause;

    /**
     * Constructor
     *
     * @param issue to report under
     * @param cause that brought it about, may be null
     */
    private TruncationOutcome(final GtfsPlanitEntityIssue issue, final GtfsParseIssue cause) {
      this.issue = issue;
      this.cause = cause;
    }

    /**
     * Collect the issue to report under
     *
     * @return issue
     */
    GtfsPlanitEntityIssue getIssue() {
      return issue;
    }

    /**
     * Collect the subdivision to report within, being what befell the stops behind the schedule
     *
     * @return subtype
     */
    String getSubType() {
      return cause != null ? cause.name() : CAUSE_NOT_RECORDED_SUBTYPE;
    }

    /**
     * Collect what the removal says about the parser, which is whatever its cause says. A schedule cut back around a
     * stop that should have been usable is a loss that need not have happened, whatever the schedule itself was
     *
     * @return disposition
     */
    GtfsIssueDisposition getDisposition() {
      return cause != null ? cause.getDisposition() : issue.getDisposition();
    }
  }

  /**
   * Create a set that holds entities by identity rather than by id, which changes when survivors are renumbered
   *
   * @param <E> type of entity
   * @return created set
   */
  private static <E> Set<E> createIdentitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  /**
   * Apply the given action to every routed service across all layers and modes
   *
   * @param routedServices to traverse
   * @param action to apply
   */
  private static void forEachRoutedService(
      final RoutedServices routedServices, final Consumer<RoutedService> action) {
    for (var layer : routedServices.getLayers()) {
      for (var modeServices : layer) {
        modeServices.forEach(action);
      }
    }
  }

  /**
   * Constructor
   *
   * @param diagnostics to record what is removed into
   */
  GtfsCleanUpDiagnostics(final GtfsPlanitEntityDiagnostics diagnostics) {
    this.diagnostics = diagnostics;
  }

  /**
   * Collect the service nodes present across all layers
   *
   * @param serviceNetwork to collect from
   * @return service nodes present
   */
  static Set<ServiceNode> collectServiceNodes(final ServiceNetwork serviceNetwork) {
    Set<ServiceNode> serviceNodes = createIdentitySet();
    serviceNetwork.getTransportLayers().forEach(layer -> layer.getServiceNodes().forEach(serviceNodes::add));
    return serviceNodes;
  }

  /**
   * Collect the routed services present across all layers and modes
   *
   * @param routedServices to collect from
   * @return routed services present
   */
  static Set<RoutedService> collectRoutedServices(final RoutedServices routedServices) {
    Set<RoutedService> services = createIdentitySet();
    forEachRoutedService(routedServices, services::add);
    return services;
  }

  /**
   * Collect the schedule based trips present across all layers and modes
   *
   * @param routedServices to collect from
   * @return trip schedules present
   */
  static Set<RoutedTripSchedule> collectTripSchedules(final RoutedServices routedServices) {
    Set<RoutedTripSchedule> tripSchedules = createIdentitySet();
    forEachRoutedService(
        routedServices, service -> service.getTripInfo().getScheduleBasedTrips().forEach(tripSchedules::add));
    return tripSchedules;
  }

  /**
   * Collect the departures of the schedule based trips present across all layers and modes
   *
   * @param routedServices to collect from
   * @return departures present
   */
  static Set<RoutedTripDeparture> collectDepartures(final RoutedServices routedServices) {
    Set<RoutedTripDeparture> departures = createIdentitySet();
    forEachRoutedService(routedServices, service -> service.getTripInfo().getScheduleBasedTrips().forEach(
        tripSchedule -> tripSchedule.getDepartures().forEach(departures::add)));
    return departures;
  }

  /**
   * Record how many entities of a type a clean-up step was presented with, how many it left behind, and which ones it
   * took away.
   * <p>
   * A removed entity is recorded under its PLANit id, which is what the surrounding log speaks in, with its ids in
   * full as the detail that accompanies it into a listing
   * </p>
   *
   * @param <E> type of entity
   * @param entityType concerned
   * @param issue the step removes the entities under
   * @param before entities present before the step ran
   * @param after entities present after the step ran
   */
  <E extends ExternalIdAble> void registerRemoved(
      final GtfsPlanitEntityType entityType, final GtfsPlanitEntityIssue issue,
      final Set<E> before, final Set<E> after) {
    diagnostics.registerDesired(entityType, before.size());
    diagnostics.registerCreated(entityType, after.size());
    before.stream().filter(entity -> !after.contains(entity)).forEach(
        entity -> diagnostics.registerIssue(
            issue, String.valueOf(entity.getId()), entity.getIdsAsString()));
  }

  /**
   * Record what the truncation took away, each removal under the issue its own outcome names and within the
   * subdivision it belongs to.
   * <p>
   * Unlike the other steps this one removes entities for outcomes that differ in kind, cutting a schedule back to
   * what fits, clearing away one of which nothing fits, and failing to map a leg that ought to have been mappable,
   * so the issue is settled per entity rather than for the step as a whole
   * </p>
   *
   * @param <E> type of entity
   * @param entityType concerned
   * @param before entities present before the truncation ran
   * @param after entities present after it ran
   * @param outcomeOf what the truncation made of a removed entity, and what brought it about
   */
  <E extends ExternalIdAble> void registerTruncated(
      final GtfsPlanitEntityType entityType, final Set<E> before, final Set<E> after,
      final Function<E, TruncationOutcome> outcomeOf) {
    diagnostics.registerDesired(entityType, before.size());
    diagnostics.registerCreated(entityType, after.size());
    before.stream().filter(entity -> !after.contains(entity)).forEach(entity -> {
      var outcome = outcomeOf.apply(entity);
      diagnostics.registerIssueWithSubType(
          outcome.getIssue(), String.valueOf(entity.getId()), outcome.getSubType(), outcome.getDisposition(),
          entity.getIdsAsString());
    });
  }

  /**
   * Capture the GTFS stops each trip schedule runs between, before any clean up has run.
   * <p>
   * Taken this early because the clean up that precedes the truncation detaches a leg from the trip the moment it
   * becomes unmapped, and a detached leg no longer names the stops it ran between. By the time the truncation is
   * judged, the very legs whose loss is to be explained have already lost the means to explain it
   * </p>
   *
   * @param routedServices to capture from
   * @param serviceNodeToGtfsStopIdMapping to trace a service node back to the stop it stands for
   * @return the pair of stops each leg of a schedule runs between, by leg, held by identity as a removal renumbers
   *     what survives. Positions are kept so a leg found unmapped later can be matched to the stops it ran between
   */
  static Map<RoutedTripSchedule, List<Pair<String, String>>> captureGtfsStopIdsBySchedule(
      final RoutedServices routedServices, final Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping) {
    Map<RoutedTripSchedule, List<Pair<String, String>>> gtfsStopIdsBySchedule = new IdentityHashMap<>();
    forEachRoutedService(routedServices, service -> service.getTripInfo().getScheduleBasedTrips().forEach(
        schedule -> gtfsStopIdsBySchedule.put(
            schedule, collectGtfsStopIds(schedule, serviceNodeToGtfsStopIdMapping))));
    return gtfsStopIdsBySchedule;
  }

  /**
   * Collect the GTFS stops a single trip schedule runs between
   *
   * @param schedule to collect for
   * @param serviceNodeToGtfsStopIdMapping to trace a service node back to the stop it stands for
   * @return the pair of stops each leg runs between, in leg order, a pair holding nulls where the leg names neither
   */
  private static List<Pair<String, String>> collectGtfsStopIds(
      final RoutedTripSchedule schedule, final Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping) {
    List<Pair<String, String>> gtfsStopIdsByLeg = new ArrayList<>(schedule.getRelativeLegTimingsSize());
    for (int index = 0; index < schedule.getRelativeLegTimingsSize(); ++index) {
      var legTiming = schedule.getRelativeLegTiming(index);
      if (!legTiming.hasParentLegSegment()
          || !legTiming.getParentLegSegment().hasParent()
          || !legTiming.getParentLegSegment().getParent().hasVertices()) {
        gtfsStopIdsByLeg.add(Pair.of(null, null));
        continue;
      }
      var legSegment = legTiming.getParentLegSegment();
      gtfsStopIdsByLeg.add(Pair.of(
          serviceNodeToGtfsStopIdMapping.apply(legSegment.getUpstreamServiceNode()),
          serviceNodeToGtfsStopIdMapping.apply(legSegment.getDownstreamServiceNode())));
    }
    return gtfsStopIdsByLeg;
  }

  /**
   * Determine which outcome the truncation will hand each trip schedule, before it runs.
   * <p>
   * A schedule keeping at least one leg is cut back to it and lives on in some form. One keeping none is removed
   * outright, and then it matters whether any two of its stops ran one after the other within the area: where none
   * did, no leg could have existed and the boundary accounts for the loss, whereas where two did and there is still
   * no leg between them nothing about where the trip ran explains it
   * </p>
   * <p>
   * Taken before the truncation because it clears away what it removes, and judged by the same test the modifier
   * itself applies, so this classifies rather than guesses
   * </p>
   *
   * @param routedServices to classify the schedules of
   * @param gtfsDiagnostics holding where each GTFS trip stood and what befell each GTFS stop
   * @param gtfsStopIdsBySchedule the stops each leg of each schedule ran between, captured before the clean up began
   * @return outcome per schedule, held by identity as a removal renumbers what survives
   */
  static Map<RoutedTripSchedule, TruncationOutcome> classifyTruncationOutcomes(
      final RoutedServices routedServices, final GtfsParseDiagnostics gtfsDiagnostics,
      final Map<RoutedTripSchedule, List<Pair<String, String>>> gtfsStopIdsBySchedule) {
    Map<RoutedTripSchedule, TruncationOutcome> outcomes = new IdentityHashMap<>();
    for (var layer : routedServices.getLayers()) {
      var serviceNodes = layer.getParentLayer().getServiceNodes();
      var legSegments = layer.getParentLayer().getLegSegments();
      for (var modeServices : layer) {
        modeServices.forEach(service -> service.getTripInfo().getScheduleBasedTrips().forEach(
            schedule -> outcomes.put(
                schedule, classifyTruncationOutcome(
                    schedule, legSegments, serviceNodes, gtfsDiagnostics,
                    gtfsStopIdsBySchedule.get(schedule)))));
      }
    }
    return outcomes;
  }

  /**
   * Determine which outcome the truncation will hand a single trip schedule
   *
   * @param schedule to classify
   * @param legSegments of the service network as it stands
   * @param serviceNodes of the service network as it stands
   * @param gtfsDiagnostics holding where each GTFS trip stood
   * @return issue it is to be reported under
   */
  private static TruncationOutcome classifyTruncationOutcome(
      final RoutedTripSchedule schedule, final ServiceLegSegments legSegments, final ServiceNodes serviceNodes,
      final GtfsParseDiagnostics gtfsDiagnostics, final List<Pair<String, String>> gtfsStopIdsByLeg) {

    int legTimings = schedule.getRelativeLegTimingsSize();
    boolean anyLegMapped = false;
    boolean anyConsecutiveStopsWithinArea = false;
    GtfsParseIssue cause = null;

    for (int index = 0; index < legTimings; ++index) {
      boolean legMapped = RelativeLegTimingUtils.isLegTimingMappedToServiceNetwork(
          schedule.getRelativeLegTiming(index), legSegments, serviceNodes);
      anyLegMapped |= legMapped;
      if (legMapped || gtfsStopIdsByLeg == null || index >= gtfsStopIdsByLeg.size()) {
        continue;
      }

      /* the leg is gone, so what it ran between is read from what was captured of it rather than from the leg
       * itself, which no longer names its stops */
      var gtfsStopIds = gtfsStopIdsByLeg.get(index);
      var upstreamCause = gtfsDiagnostics.getDiscardIssue(GtfsObjectType.STOP, gtfsStopIds.first());
      var downstreamCause = gtfsDiagnostics.getDiscardIssue(GtfsObjectType.STOP, gtfsStopIds.second());
      cause = mostProblematic(cause, mostProblematic(upstreamCause, downstreamCause));

      /* two stops follow one another in the trip exactly when a single leg joins them, and a stop the run kept is
       * one it could place, so a leg both of whose stops were kept should have been mappable */
      anyConsecutiveStopsWithinArea |=
          gtfsStopIds.first() != null && gtfsStopIds.second() != null
              && upstreamCause == null && downstreamCause == null;
    }

    return new TruncationOutcome(
        determineTruncationIssue(
            schedule, anyLegMapped, anyConsecutiveStopsWithinArea, gtfsDiagnostics),
        cause);
  }

  /**
   * Determine which outcome a trip schedule met, given what became of its legs
   *
   * @param schedule concerned
   * @param anyLegMapped whether any leg of it survived
   * @param anyConsecutiveStopsWithinArea whether any two of its stops ran one after the other within the area
   * @param gtfsDiagnostics holding where each GTFS trip stood
   * @return issue it is to be reported under
   */
  private static GtfsPlanitEntityIssue determineTruncationIssue(
      final RoutedTripSchedule schedule, final boolean anyLegMapped,
      final boolean anyConsecutiveStopsWithinArea, final GtfsParseDiagnostics gtfsDiagnostics) {
    if (anyLegMapped) {
      return GtfsPlanitEntityIssue.TRIP_SCHEDULE_TRUNCATED_TO_NETWORK;
    }
    if (anyConsecutiveStopsWithinArea) {
      return GtfsPlanitEntityIssue.TRIP_SCHEDULE_UNMAPPED_DESPITE_CONSECUTIVE_STOPS;
    }

    /* a trip that never reached the area was not cut back to nothing, it was never a candidate for cutting back at
     * all, and calling its removal a failed truncation would read as though something had been attempted */
    return determineFurthestOutsideScope(schedule, gtfsDiagnostics) == GtfsEntityScope.OUT
        ? GtfsPlanitEntityIssue.TRIP_SCHEDULE_REMOVED_OUTSIDE_NETWORK_AREA
        : GtfsPlanitEntityIssue.TRIP_SCHEDULE_REMOVED_TRUNCATION_NOT_VIABLE;
  }

  /**
   * Collect whichever of the two causes says most about the parser, an absent cause not competing.
   * <p>
   * The opposite of how a leg segment weighs its endpoints, and deliberately so. A leg segment is lost whole and its
   * endpoints offer competing explanations for that one loss, so an endpoint legitimately outside the area settles
   * it. A trip schedule is cut rather than lost, and truncating away what lies outside the area is the run working
   * as asked, so that cause is discharged and explains nothing of what else went missing. What remains is a stop
   * that should have been usable and was not, which is loss that need not have happened
   * </p>
   *
   * @param current most telling so far, may be null
   * @param candidate to weigh against it, may be null
   * @return the more telling of the two, null only when both are absent
   */
  private static GtfsParseIssue mostProblematic(final GtfsParseIssue current, final GtfsParseIssue candidate) {
    if (candidate == null) {
      return current;
    }
    if (current == null) {
      return candidate;
    }
    /* dispositions are declared from the most deliberate to the least, so the later of the two says more */
    return candidate.getDisposition().ordinal() > current.getDisposition().ordinal() ? candidate : current;
  }

  /**
   * Determine where the GTFS trips behind a trip schedule stood relative to the area the run covers.
   * <p>
   * Only the spatial respect is asked. A trip ruled out on any other never became a schedule at all, so among the
   * schedules that existed to be truncated the remaining respects hold nothing but IN, and reporting them would
   * divide the count along an axis every occurrence shares
   * </p>
   * <p>
   * A schedule may stand for several trips once identically scheduled ones have been consolidated, and then the trip
   * furthest outside the area decides: a schedule holding a trip that only ever ran partly within it was always
   * going to be cut back, so reading that as a failure to map would overstate what is left to fix
   * </p>
   *
   * @param schedule concerned
   * @param gtfsDiagnostics holding where each GTFS trip stood
   * @return subtype to report the removal within
   */
  static String determineTruncatedScheduleSubType(
      final RoutedTripSchedule schedule, final GtfsParseDiagnostics gtfsDiagnostics) {
    var furthestOutside = determineFurthestOutsideScope(schedule, gtfsDiagnostics);
    return furthestOutside != null
        ? GtfsScopeDimension.SPATIAL.name() + "_" + furthestOutside.name() : TRIP_NOT_RECORDED_SUBTYPE;
  }

  /**
   * Collect where the GTFS trips behind a trip schedule stood spatially, the one furthest outside the area deciding
   *
   * @param schedule concerned
   * @param gtfsDiagnostics holding where each GTFS trip stood
   * @return scope, null where nothing is recorded of any trip behind it
   */
  private static GtfsEntityScope determineFurthestOutsideScope(
      final RoutedTripSchedule schedule, final GtfsParseDiagnostics gtfsDiagnostics) {
    if (!schedule.hasExternalId()) {
      return null;
    }

    GtfsEntityScope furthestOutside = null;
    for (var gtfsTripId : schedule.getExternalId().split(String.valueOf(CharacterUtils.COMMA))) {
      var state = gtfsDiagnostics.getSettledState(GtfsObjectType.TRIP, gtfsTripId.trim());
      if (state == null) {
        continue;
      }
      furthestOutside = furthestOutside(furthestOutside, state.get(GtfsScopeDimension.SPATIAL));
    }
    return furthestOutside;
  }

  /**
   * Collect whichever of the two scopes sits furthest outside the area the run covers, an absent scope not competing
   *
   * @param current furthest so far, may be null
   * @param candidate to weigh against it, may be null
   * @return the furthest outside of the two, null only when neither says where it stood
   */
  private static GtfsEntityScope furthestOutside(
      final GtfsEntityScope current, final GtfsEntityScope candidate) {
    if (candidate == null) {
      return current;
    }
    int candidateRank = SCOPES_FURTHEST_OUTSIDE_FIRST.indexOf(candidate);
    if (candidateRank < 0) {
      /* the scope says nothing about where the entity stood, so it cannot be furthest anywhere */
      return current;
    }
    if (current == null) {
      return candidate;
    }
    int currentRank = SCOPES_FURTHEST_OUTSIDE_FIRST.indexOf(current);
    return currentRank < 0 || candidateRank < currentRank ? candidate : current;
  }
}