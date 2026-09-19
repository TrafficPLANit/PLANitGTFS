package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.gtfs.enums.GtfsObjectType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Every way in which a GTFS entity can fail to arrive in the memory model intact, whether because the run asked for it,
 * because the parser cannot represent it, or because something went wrong.
 * <p>
 * An issue does not always cost the entity. A trip whose schedule survives while one of its legs has no physical path
 * is degraded rather than lost, and reporting only outright losses would leave that invisible. Hence each issue states
 * whether it discards the entity: discarding issues account for the difference between what was requested and what was
 * parsed, while the remainder are recorded against entities that were parsed regardless.
 * </p>
 * <p>
 * Each issue also owns the wording used when it is reported, including the template for the entity specific context a
 * call site supplies. Holding it here rather than at the call site means an issue registered from several places reads
 * the same way from each of them, and that the wording can be changed in one place.
 * </p>
 *
 * @author markr
 */
public enum GtfsParseIssue implements GtfsIssue {

  /* SERVICES - routes */

  /** route excluded through the reader settings */
  ROUTE_EXCLUDED_BY_SETTINGS(
      GtfsParseStage.SERVICES, GtfsObjectType.ROUTE, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Route excluded by settings", null),

  /** route's mode is not among the activated modes for this run */
  ROUTE_MODE_NOT_ACTIVATED(
      GtfsParseStage.SERVICES, GtfsObjectType.ROUTE, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Route mode not activated", null),

  /** every trip of the route runs wholly beyond the area the run covers */
  ROUTE_OUTSIDE_BOUNDING_AREA(
      GtfsParseStage.SERVICES, GtfsObjectType.ROUTE, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Route runs wholly outside bounding area", null),

  /** route retained no trips once its trips were filtered */
  ROUTE_WITHOUT_TRIPS(
      GtfsParseStage.SERVICES, GtfsObjectType.ROUTE, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Route without any retained trips", null),

  /** route's mode is activated but no routed services layer supports it */
  ROUTE_NO_SERVICES_LAYER_FOR_MODE(
      GtfsParseStage.SERVICES, GtfsObjectType.ROUTE, GtfsIssueDisposition.LIMITATION, true,
      GtfsIssueLogPolicy.IMMEDIATE, "No PLANit layer available for PLANit mode mapped from GTFS route type",
      "PLANit mode %s mapped from GTFS route type %s"),

  /* SERVICES - trips */

  /** trip belongs to a route that was itself discarded */
  TRIP_ROUTE_DISCARDED(
      GtfsParseStage.SERVICES, GtfsObjectType.TRIP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Trip's route was discarded", "route %s"),

  /** trip belongs to a route whose mode is not activated */
  TRIP_ROUTE_MODE_NOT_ACTIVATED(
      GtfsParseStage.SERVICES, GtfsObjectType.TRIP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Trip's route mode not activated", "route %s"),

  /** trip belongs to a route whose mode has no routed services layer to hold it */
  TRIP_ROUTE_WITHOUT_SERVICES_LAYER(
      GtfsParseStage.SERVICES, GtfsObjectType.TRIP, GtfsIssueDisposition.LIMITATION, true,
      GtfsIssueLogPolicy.COLLATED, "Trip's route mode has no routed services layer", "route %s"),

  /** trip references a route that is absent from the memory model without any recorded cause */
  TRIP_ROUTE_MISSING_UNEXPLAINED(
      GtfsParseStage.SERVICES, GtfsObjectType.TRIP, GtfsIssueDisposition.PROBLEM, true,
      GtfsIssueLogPolicy.IMMEDIATE, "Unable to find GTFS route removal reason for GTFS trip, this should not happen", "route %s"),

  /** trip's service id is not active on the day the run was configured for */
  TRIP_SERVICE_ID_NOT_ACTIVE_ON_DAY(
      GtfsParseStage.SERVICES, GtfsObjectType.TRIP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Trip's service id not active on chosen day", null),

  /** trip falls outside the time period the run was configured for */
  TRIP_OUTSIDE_TIME_PERIOD(
      GtfsParseStage.SERVICES, GtfsObjectType.TRIP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Trip outside chosen time period", null),

  /** every stop of the trip lies beyond the area the run covers */
  TRIP_OUTSIDE_BOUNDING_AREA(
      GtfsParseStage.SERVICES, GtfsObjectType.TRIP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Trip runs wholly outside bounding area", null),

  /** trip yielded no service legs */
  TRIP_WITHOUT_LEGS(
      GtfsParseStage.SERVICES, GtfsObjectType.TRIP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Trip without any service legs", null),

  /** trip spans a leg lasting a day or longer, which cannot be represented */
  TRIP_LEG_DURATION_EXCEEDS_DAY(
      GtfsParseStage.SERVICES, GtfsObjectType.TRIP, GtfsIssueDisposition.LIMITATION, true,
      GtfsIssueLogPolicy.COLLATED, "Trip leg duration of a day or more is unsupported", null,
      "duration (%3$s) between stops (%1$s, %2$s) and/or dwell time at stop (%4$s) should be less than a day"),

  /* SERVICES - stop times */

  /**
   * Stop time belongs to a trip that was discarded. Registered for every stop time of every discarded trip, so it
   * deliberately carries no template: composing context for an occurrence arriving in the millions costs more than the
   * context is worth, and the trip's own discard already records the reason
   */
  STOP_TIME_OF_DISCARDED_TRIP(
      GtfsParseStage.SERVICES, GtfsObjectType.STOP_TIME, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Stop time of a discarded trip", null),

  /** stop time repeats one already parsed for the same trip */
  STOP_TIME_DUPLICATE(
      GtfsParseStage.SERVICES, GtfsObjectType.STOP_TIME, GtfsIssueDisposition.PROBLEM, true,
      GtfsIssueLogPolicy.COLLATED, "Duplicate stop time", "stop %s at sequence position %s"),

  /** stop times of a trip are not consecutive, which cannot be represented */
  STOP_TIME_NON_CONSECUTIVE(
      GtfsParseStage.SERVICES, GtfsObjectType.STOP_TIME, GtfsIssueDisposition.LIMITATION, true,
      GtfsIssueLogPolicy.COLLATED, "GTFS trip's stop times not consecutive, not yet supported", null),

  /** unable to find the GTFS trip the stop time belongs to, so there is nothing to attach it to */
  STOP_TIME_TRIP_UNRESOLVED(
      GtfsParseStage.SERVICES, GtfsObjectType.STOP_TIME, GtfsIssueDisposition.PROBLEM, true,
      GtfsIssueLogPolicy.COLLATED, "Unable to find GTFS trip for GTFS stop time", null),

  /** unable to find the GTFS route of the stop time's trip in the PLANit memory model */
  STOP_TIME_ROUTE_UNRESOLVED(
      GtfsParseStage.SERVICES, GtfsObjectType.STOP_TIME, GtfsIssueDisposition.PROBLEM, true,
      GtfsIssueLogPolicy.COLLATED,
      "Unable to find GTFS route in PLANit memory model corresponding to GTFS trip",
      "route %1$s, stop %2$s"),

  /* SERVICES - calendars */

  /** calendar activates no day the run was configured for */
  CALENDAR_NOT_ACTIVE_ON_DAY(
      GtfsParseStage.SERVICES, GtfsObjectType.CALENDAR, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Calendar not active on chosen day", null),

  /*
   * STOP
   *
   * Every issue of this stage takes the stop's name, longitude and latitude as its first three arguments, followed by
   * whatever the issue itself needs. A persisted occurrence therefore always names and locates the stop it concerns,
   * while the logged form selects only what fits a line, e.g. %4$s for the first issue specific argument
   */

  /**
   * Multiple GTFS stops found for the same GTFS STOP_ID. Only the last is kept and the earlier duplicate entry is
   * ignored, so whichever of the two the rest of the feed meant to reference is a coin toss
   */
  STOP_DUPLICATE_ID(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, true,
      GtfsIssueLogPolicy.COLLATED, "Multiple GTFS stops found for the same GTFS STOP_ID", null,
      "ignored duplicate entry %1$s"),

  /**
   * Stop lies well outside the bounding area the run was configured for, a feed covering more ground than the network
   * being the normal case rather than a fault. Counted so that the stops of the feed add up, and nothing more: naming
   * individual stops of another region tells nobody anything
   */
  STOP_OUTSIDE_BOUNDING_AREA(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.SILENT_COUNT_ONLY, "Stop outside bounding area", null,
      "stop %1$s at (%2$s, %3$s), %4$sm from bounding area edge"),

  /**
   * Stop lies just outside the bounding area, close enough that the area being drawn slightly differently would have
   * included it. Worth listing, unlike a stop in another region
   */
  STOP_JUST_OUTSIDE_BOUNDING_AREA(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Stop just outside bounding area", "%4$sm from bounding area edge",
      "stop %1$s at (%2$s, %3$s), %4$sm from bounding area edge"),

  /** stop serves only modes that are not activated for this run */
  STOP_MODE_NOT_ACTIVATED(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Stop serves no activated mode", null,
      "stop %1$s at (%2$s, %3$s)"),

  /**
   * No parsed routed service visits the stop, i.e. the service node it would sit on carries no service in the memory
   * model. Whether the feed never served it, or a service that did was lost earlier in the services stage, cannot be
   * told apart from here, which is why this states what was observed rather than a cause
   */
  STOP_NOT_SERVED_BY_ANY_PARSED_SERVICE(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.LIMITATION, true,
      GtfsIssueLogPolicy.COLLATED, "Stop not served by any parsed service", null,
      "stop %1$s at (%2$s, %3$s)"),

  /** stop's location type is one the parser does not support, e.g. a station or an entrance */
  STOP_UNSUPPORTED_LOCATION_TYPE(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.LIMITATION, true,
      GtfsIssueLogPolicy.COLLATED, "Stop location type is unsupported", "location type %4$s",
      "stop %1$s at (%2$s, %3$s), location type %4$s"),

  /** stop excluded through the reader settings */
  STOP_EXCLUDED_BY_SETTINGS(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Stop excluded by settings", null,
      "stop %1$s at (%2$s, %3$s)"),

  /**
   * Stop could not be attached to the network at all. The loss of a stop is recorded here once, whatever the number of
   * modes that failed to find a way onto the network, those being reported separately per mode
   */
  STOP_NO_CONNECTOID_LOCATION(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, true,
      GtfsIssueLogPolicy.COLLATED, "No connectoid location could be found for GTFS stop",
      "%4$sm from bounding area edge",
      "stop %1$s at (%2$s, %3$s), %4$sm from bounding area edge, nearby links %5$s"),

  /**
   * Stop could not be attached to the network while sitting against the edge of the bounding area, where the network
   * is truncated by the area that was asked for rather than by anything about the stop
   */
  STOP_UNMAPPED_AT_BOUNDING_AREA_EDGE(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.BY_DESIGN, true,
      GtfsIssueLogPolicy.COLLATED, "Stop at the bounding area edge could not be attached to the network",
      "%4$sm from bounding area edge",
      "stop %1$s at (%2$s, %3$s), %4$sm from bounding area edge, nearby links %5$s"),

  /**
   * No appropriate access link was found for one of the stop's modes. Not a discard in itself, since another mode may
   * still put the stop on the network; reported only when the stop was lost, so that the modes responsible are known
   */
  STOP_MODE_WITHOUT_ACCESS_LINK(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.COLLATED, "No access link found for stop's mode", "mode %4$s",
      "stop %1$s at (%2$s, %3$s), mode %4$s"),

  /**
   * No connectoid location could be found on the access link of one of the stop's modes. Not a discard in itself, for
   * the same reason as {@link #STOP_MODE_WITHOUT_ACCESS_LINK}
   */
  STOP_MODE_WITHOUT_CONNECTOID_LOCATION(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.COLLATED, "No connectoid location found for stop's mode", "mode %4$s",
      "stop %1$s at (%2$s, %3$s), mode %4$s"),

  /**
   * Settings pin the stop to a link that does not exist in the network, so the instruction cannot be carried out and
   * the stop is mapped as any other would be. A configuration error, hence reported the moment it arises
   */
  STOP_OVERWRITTEN_LINK_MAPPING_NOT_FOUND(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.IMMEDIATE, "Unable to find manually overwritten link mapping for GTFS stop", null,
      "stop %1$s at (%2$s, %3$s)"),

  /**
   * A nearby transfer zone was found but the stop could not be attached to it, nor could one be created for it. Not a
   * discard in itself: the loss of the stop is already recorded where the attempt to create its own zone failed, and
   * this states the aggravating circumstance that usable zones were sitting nearby
   */
  STOP_NEARBY_TRANSFER_ZONE_UNUSABLE(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.COLLATED, "Unable to add TransferZone for GTFS stop despite nearby transfer zones",
      "%4$s nearby transfer zone(s)",
      "stop %1$s at (%2$s, %3$s), %4$s nearby transfer zone(s): %5$s"),

  /**
   * Settings manually map the stop to a transfer zone that does not exist in the zoning, so the instruction cannot be
   * carried out. A configuration error rather than a feed or parser issue, hence reported the moment it arises
   */
  STOP_OVERWRITTEN_TRANSFER_ZONE_NOT_FOUND(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.IMMEDIATE, "Manually mapped transfer zone for stop not found",
      "transfer zone (%4$s, %5$s)",
      "stop %1$s at (%2$s, %3$s), transfer zone (%4$s, %5$s)"),

  /** no link permitting the stop's mode was found within the search radius */
  STOP_NO_MODE_COMPATIBLE_LINK_IN_RADIUS(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, true,
      GtfsIssueLogPolicy.COLLATED, "No nearby links found for GTFS stop within search radius", null,
      "stop %1$s at (%2$s, %3$s)"),

  /**
   * A transfer zone was created for the stop but no connectoid attached it to the physical network. The zone is kept
   * and the stop mapped to it, so not a discard, but it is unreachable and should not arise
   */
  STOP_NO_ACCESS_CONNECTOID_CREATED(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.IMMEDIATE, "No access connectoid established for stop", "mode(s) %4$s",
      "stop %1$s at (%2$s, %3$s), mode(s) %4$s"),

  /**
   * A nearby transfer zone was found for the stop but it offers no access link segments, so it cannot be matched to.
   * Not a discard: the stop falls back on having a transfer zone of its own created for it
   */
  STOP_NEARBY_TRANSFER_ZONE_WITHOUT_ACCESS_SEGMENTS(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.COLLATED, "Nearby transfer zone without access link segments", null,
      "stop %1$s at (%2$s, %3$s)"),

  /**
   * Stop was matched to a transfer zone another stop already maps to. Kept, since joined mapping is legitimate for a
   * multi platform stop, but worth verifying for a road mode where a single mapping is expected
   */
  STOP_TRANSFER_ZONE_SHARED_WITH_OTHER_STOP(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.COLLATED, "Transfer zone already mapped to another stop",
      "transfer zone (%4$s), already mapped to stop %5$s",
      "stop %1$s at (%2$s, %3$s), transfer zone (%4$s), already mapped to stop %5$s %6$s at (%7$s)"),

  /** stop was mapped but its position relative to the network suggests it sits on the wrong side of the road */
  STOP_POSSIBLY_ON_WRONG_SIDE_OF_ROAD(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.COLLATED, "GTFS stop may be in wrong location/wrong side of modelled road", "selected link (%4$s)",
      "stop %1$s at (%2$s, %3$s), selected access link (%4$s) named %5$s is not the closest link (%6$s)"),

  /** stop was mapped but the preferred access link segment is not adjacent to the chosen access node */
  STOP_PREFERRED_ACCESS_LINK_SEGMENT_NOT_ADJACENT(
      GtfsParseStage.STOP, GtfsObjectType.STOP, GtfsIssueDisposition.PROBLEM, false,
      GtfsIssueLogPolicy.COLLATED, "Preferred access link segment not adjacent",
      "transfer zone (%4$s)",
      "stop %1$s at (%2$s, %3$s), transfer zone (%4$s) named %5$s, preferred access link segment (%6$s)");

  /** stage the issue arises in */
  private final GtfsParseStage stage;

  /** GTFS entity type the issue applies to */
  private final GtfsObjectType entityType;

  /** what the issue says about the parser */
  private final GtfsIssueDisposition disposition;

  /** whether the entity is lost as a result */
  private final boolean discarding;

  /** how the issue reaches the log */
  private final GtfsIssueLogPolicy logPolicy;

  /** readable description used when reporting */
  private final String description;

  /**
   * Entity specific context accompanying the description, in the form a log line has room for and in the fuller form
   * the persisted listing can afford, a listing being read on its own without the surrounding parse
   */
  private final GtfsIssueDetail detail;

  /**
   * Constructor for an issue whose persisted context is the same as its logged context
   *
   * @param stage the issue arises in
   * @param entityType GTFS entity type the issue applies to
   * @param disposition what the issue says about the parser
   * @param discarding whether the entity is lost as a result
   * @param logPolicy how the issue reaches the log
   * @param description readable description used when reporting
   * @param detailTemplate format of the entity specific context, null when the issue takes none
   */
  GtfsParseIssue(
      final GtfsParseStage stage,
      final GtfsObjectType entityType,
      final GtfsIssueDisposition disposition,
      final boolean discarding,
      final GtfsIssueLogPolicy logPolicy,
      final String description,
      final String detailTemplate) {
    this(stage, entityType, disposition, discarding, logPolicy, description, detailTemplate, null);
  }

  /**
   * Constructor
   * <p>
   * Both templates are composed from the same arguments, so a template that wants only some of them selects those
   * positionally, e.g. {@code %2$s}. A template using positional references must use them throughout, since mixing the
   * two forms makes which argument lands where depend on their order in the template
   * </p>
   *
   * @param stage the issue arises in
   * @param entityType GTFS entity type the issue applies to
   * @param disposition what the issue says about the parser
   * @param discarding whether the entity is lost as a result
   * @param logPolicy how the issue reaches the log
   * @param description readable description used when reporting
   * @param detailTemplate format of the entity specific context, null when the issue takes none
   * @param persistedDetailTemplate format of the entity specific context when persisted, null to use the logged one
   */
  GtfsParseIssue(
      final GtfsParseStage stage,
      final GtfsObjectType entityType,
      final GtfsIssueDisposition disposition,
      final boolean discarding,
      final GtfsIssueLogPolicy logPolicy,
      final String description,
      final String detailTemplate,
      final String persistedDetailTemplate) {
    this.stage = stage;
    this.entityType = entityType;
    this.disposition = disposition;
    this.discarding = discarding;
    this.logPolicy = logPolicy;
    this.description = description;
    this.detail = GtfsIssueDetail.create(detailTemplate, persistedDetailTemplate);
  }

  /**
   * Compose the entity specific context accompanying this issue from the arguments its template expects
   *
   * @param detailArgs to compose from, none when the issue takes no context
   * @return composed detail, null when the issue takes no context
   */
  @Override
  public String createDetail(final Object... detailArgs) {
    return detail.createLogged(name(), detailArgs);
  }

  /**
   * Compose the entity specific context accompanying this issue when persisted, from the same arguments its logged
   * context is composed from
   *
   * @param detailArgs to compose from, none when the issue takes no context
   * @return composed detail, null when the issue takes no context
   */
  @Override
  public String createPersistedDetail(final Object... detailArgs) {
    return detail.createPersisted(name(), detailArgs);
  }

  /**
   * Collect the stage the issue arises in
   *
   * @return stage
   */
  public GtfsParseStage getStage() {
    return stage;
  }

  /**
   * Collect the GTFS entity type the issue applies to
   *
   * @return entity type
   */
  public GtfsObjectType getEntityType() {
    return entityType;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getEntityLabel() {
    return entityType.toString().toLowerCase();
  }

  /**
   * Collect what the issue says about the parser
   *
   * @return disposition
   */
  @Override
  public GtfsIssueDisposition getDisposition() {
    return disposition;
  }

  /**
   * Verify whether the entity is lost as a result of this issue, as opposed to being parsed while carrying it
   *
   * @return true when the entity is discarded, false otherwise
   */
  @Override
  public boolean isDiscarding() {
    return discarding;
  }

  /**
   * Collect how the issue reaches the log
   *
   * @return log policy
   */
  @Override
  public GtfsIssueLogPolicy getLogPolicy() {
    return logPolicy;
  }

  /**
   * Collect the readable description used when reporting
   *
   * @return description
   */
  @Override
  public String getDescription() {
    return description;
  }

  /**
   * Collect the format of the entity specific context accompanying the description
   *
   * @return detail template, null when the issue takes none
   */
  public String getDetailTemplate() {
    return detail.getLoggedTemplate();
  }

  /**
   * Verify whether the issue accompanies its description with entity specific context
   *
   * @return true when it does, false otherwise
   */
  @Override
  public boolean hasDetailTemplate() {
    return detail.hasLoggedTemplate();
  }

  /**
   * Collect the format of the entity specific context accompanying the description when persisted
   *
   * @return persisted detail template, null when the logged one is used
   */
  public String getPersistedDetailTemplate() {
    return detail.getPersistedTemplate();
  }

  /**
   * Verify whether the issue accompanies its description with entity specific context when persisted, which it does
   * whenever either template is present since the persisted form falls back on the logged one
   *
   * @return true when it does, false otherwise
   */
  @Override
  public boolean hasPersistedDetailTemplate() {
    return detail.hasPersistedTemplate();
  }

  /**
   * Collect how many arguments this issue's detail template expects
   *
   * @return number of arguments
   */
  public int getDetailArgCount() {
    return detail.getArgCount();
  }

  /**
   * Collect all issues arising in a given stage, in declaration order
   *
   * @param stage to collect for
   * @return issues of that stage
   */
  public static List<GtfsParseIssue> getIssuesForStage(final GtfsParseStage stage) {
    return Arrays.stream(values()).filter(issue -> issue.getStage() == stage).collect(Collectors.toList());
  }

  /**
   * Collect all issues applying to a given entity type, in declaration order
   *
   * @param entityType to collect for
   * @return issues for that entity type
   */
  public static List<GtfsParseIssue> getIssuesForEntityType(final GtfsObjectType entityType) {
    return Arrays.stream(values()).filter(issue -> issue.getEntityType() == entityType).collect(Collectors.toList());
  }
}
