package org.goplanit.gtfs.converter.diagnostics;

/**
 * Reasons a PLANit entity the converter derives from the feed either could not be built, or was lost again once the
 * result was brought in line with the physical network.
 * <p>
 * An entity that was never built has no id of its own to be reported under, so each occurrence is recorded against the
 * GTFS entities it was to be built from instead. One that existed and was subsequently removed does have such an id,
 * and is reported under it
 * </p>
 *
 * @author markr
 */
public enum GtfsPlanitEntityIssue implements GtfsIssue {

  /**
   * no transfer zone could be established for a stop the run covers, neither by matching it to a pre-existing zone nor
   * by creating one for it, so the stop cannot be boarded from anywhere. The stop stands within scope, so nothing
   * about where or when it runs accounts for this
   */
  /**
   * a transfer zone was left serving no stop once the services had been brought in line with the physical network,
   * and a zone nothing is boarded from is of no use
   */
  TRANSFER_ZONE_DANGLING_AFTER_CLEAN_UP(
      GtfsPlanitEntityType.TRANSFER_ZONE, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.PLANIT_CONSTRUCTION, true, GtfsIssueLogPolicy.COLLATED,
      "Transfer zone removed, no stop left using it", null, Templates.REMOVED_ENTITY_IDS),

  /** a connectoid was left granting access to no transfer zone any service still reaches */
  TRANSFER_ZONE_GROUP_DANGLING_AFTER_CLEAN_UP(
      GtfsPlanitEntityType.TRANSFER_ZONE_GROUP, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.PLANIT_CONSTRUCTION, true, GtfsIssueLogPolicy.COLLATED,
      "Transfer zone group removed, no transfer zone left in it", null, Templates.REMOVED_ENTITY_IDS),

  CONNECTOID_UNUSED_AFTER_CLEAN_UP(
      GtfsPlanitEntityType.CONNECTOID, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.PLANIT_CONSTRUCTION, true, GtfsIssueLogPolicy.COLLATED,
      "Connectoid removed, no longer used by any service", null, Templates.REMOVED_ENTITY_IDS),

  /** an endpoint stop of the leg segment has no transfer zone, so the network cannot be reached from it */
  LEG_SEGMENT_STOP_WITHOUT_TRANSFER_ZONE(
      GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, GtfsIssueDisposition.PROBLEM,
      GtfsIssueOrigin.GTFS_PARSING, false, GtfsIssueLogPolicy.COLLATED,
      "Endpoint stop has no transfer zone", Templates.DETAIL, Templates.PERSISTED_DETAIL),

  /** an endpoint of the leg segment has no connectoid granting access to the network for its mode */
  LEG_SEGMENT_ENDPOINT_WITHOUT_ACCESS_CONNECTOID(
      GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, GtfsIssueDisposition.PROBLEM,
      GtfsIssueOrigin.PLANIT_CONSTRUCTION, false, GtfsIssueLogPolicy.COLLATED,
      "Endpoint has no usable access connectoid", Templates.DETAIL, Templates.PERSISTED_DETAIL),

  /**
   * Unable to find available transfer zone access nodes for the leg segment, the GTFS stop likely mapped to an
   * incorrect physical access node upon an earlier path search. Unlike an endpoint without any access connectoid, the
   * transfer zone does have connectoids, just not at the node the stop was mapped to
   */
  LEG_SEGMENT_ENDPOINT_ACCESS_NODE_MISMATCH(
      GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, GtfsIssueDisposition.PROBLEM,
      GtfsIssueOrigin.PLANIT_CONSTRUCTION, false, GtfsIssueLogPolicy.COLLATED,
      "Unable to find available transfer zone access nodes, GTFS stop likely mapped to incorrect physical access node",
      Templates.DETAIL, Templates.PERSISTED_DETAIL),

  /** no physical path exists between the endpoints of the leg segment for its mode */
  LEG_SEGMENT_NO_ELIGIBLE_PHYSICAL_PATH(
      GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, GtfsIssueDisposition.PROBLEM,
      GtfsIssueOrigin.PLANIT_CONSTRUCTION, false, GtfsIssueLogPolicy.COLLATED,
      "No eligible physical path between endpoints", Templates.DETAIL, Templates.PERSISTED_DETAIL),

  /** a service node was removed because it lies beyond the area the physical network covers */
  SERVICE_NODE_OUTSIDE_NETWORK_AREA(
      GtfsPlanitEntityType.SERVICE_NODE, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.GTFS_PARSING, true, GtfsIssueLogPolicy.COLLATED,
      "Service node outside network area", null, Templates.REMOVED_ENTITY_IDS),

  /**
   * a trip schedule was left with a single stop once the trips outside the chosen period had gone, and a single stop
   * is no leg, so there is no run left to schedule
   */
  TRIP_SCHEDULE_WITHOUT_LEGS(
      GtfsPlanitEntityType.ROUTED_TRIP_SCHEDULE, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.GTFS_PARSING, true, GtfsIssueLogPolicy.COLLATED,
      "Trip schedule removed, a single stop leaves no leg", null, Templates.REMOVED_ENTITY_IDS),

  /**
   * a routed service was left with no trips at all once the trips outside the chosen period had gone, a service that
   * runs nothing not being a service. What became of the trips themselves is reported against those trips
   */
  ROUTED_SERVICE_WITHOUT_TRIPS_IN_SCOPE(
      GtfsPlanitEntityType.ROUTED_SERVICE, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.GTFS_PARSING, true, GtfsIssueLogPolicy.COLLATED,
      "Routed service removed, no trips left within the chosen scope", null, Templates.REMOVED_ENTITY_IDS),

  /**
   * a routed service was left with no trips at all once the truncation had cut back or removed each of them, and a
   * service that runs nothing is not a service. What became of the trips themselves is reported against those trips
   */
  ROUTED_SERVICE_WITHOUT_TRIPS(
      GtfsPlanitEntityType.ROUTED_SERVICE, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.GTFS_PARSING, true, GtfsIssueLogPolicy.COLLATED,
      "Routed service removed, no trips left after truncation", null, Templates.REMOVED_ENTITY_IDS),

  /**
   * a trip schedule ran beyond the network area and was cut back to the part within it, the original being replaced
   * by one viable trip per remaining run of consecutive legs, each with its departure times adjusted
   */
  TRIP_SCHEDULE_TRUNCATED_TO_NETWORK(
      GtfsPlanitEntityType.ROUTED_TRIP_SCHEDULE, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.GTFS_PARSING, true, GtfsIssueLogPolicy.COLLATED,
      "Trip schedule truncated to network area", null, Templates.REMOVED_ENTITY_IDS),

  /**
   * a trip schedule ran wholly beyond the network area, so there was never a part of it to keep and it was removed
   * outright. Distinct from a truncation that came to nothing: this one was never a candidate for cutting back
   */
  TRIP_SCHEDULE_REMOVED_OUTSIDE_NETWORK_AREA(
      GtfsPlanitEntityType.ROUTED_TRIP_SCHEDULE, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.GTFS_PARSING, true, GtfsIssueLogPolicy.COLLATED,
      "Trip schedule removed, ran wholly outside network area", null, Templates.REMOVED_ENTITY_IDS),

  /**
   * a trip schedule reached the network area and was put through the truncation, yet nothing usable was left of it,
   * no leg of it having survived, so it was removed rather than cut back
   */
  TRIP_SCHEDULE_REMOVED_TRUNCATION_NOT_VIABLE(
      GtfsPlanitEntityType.ROUTED_TRIP_SCHEDULE, GtfsIssueDisposition.PROBLEM,
      GtfsIssueOrigin.GTFS_PARSING, true, GtfsIssueLogPolicy.COLLATED,
      "Trip schedule removed, truncation left no viable trip", null, Templates.REMOVED_ENTITY_IDS),

  /**
   * a trip schedule was put through the same truncation and nothing was left of it even though two of its stops ran
   * one after the other within the network area, so a leg between them ought to have been mappable. Alone among the
   * ways a schedule is lost here this one names no boundary, and so stands against the parser rather than explaining
   * the loss away
   */
  TRIP_SCHEDULE_UNMAPPED_DESPITE_CONSECUTIVE_STOPS(
      GtfsPlanitEntityType.ROUTED_TRIP_SCHEDULE, GtfsIssueDisposition.PROBLEM,
      GtfsIssueOrigin.PLANIT_CONSTRUCTION, true, GtfsIssueLogPolicy.COLLATED,
      "Trip schedule removed, no leg mapped between consecutive stops within network area", null,
      Templates.REMOVED_ENTITY_IDS),

  /**
   * a trip schedule was folded into another running the same legs at the same relative times, the two being
   * indistinguishable once the truncation had cut each back to the part within the network area. Its departures carry
   * over to the schedule it was folded into, so what it ran is kept while the schedule itself is not
   */
  TRIP_SCHEDULE_CONSOLIDATED_INTO_IDENTICAL(
      GtfsPlanitEntityType.ROUTED_TRIP_SCHEDULE, GtfsIssueDisposition.BY_DESIGN,
      GtfsIssueOrigin.PLANIT_CONSTRUCTION, true, GtfsIssueLogPolicy.COLLATED,
      "Trip schedule consolidated into an identically scheduled one", null, Templates.REMOVED_ENTITY_IDS),

  /** several departures of a trip schedule share the same scheduled time */
  DEPARTURE_DUPLICATE_SCHEDULED_TIME(
      GtfsPlanitEntityType.DEPARTURE, GtfsIssueDisposition.PROBLEM,
      GtfsIssueOrigin.PLANIT_CONSTRUCTION, true, GtfsIssueLogPolicy.COLLATED,
      "Duplicate trip schedule departure time", null, Templates.REMOVED_ENTITY_IDS);

  /**
   * Detail templates shared by the issues above. A constant may not refer to a static field of its own enum, the
   * constants being initialised first, so they are held here instead of being repeated on each issue
   */
  private static class Templates {

    /** context accompanying the description in a log line, which has room only for what separates one case from another */
    private static final String DETAIL = "mode %1$s";

    /**
     * Context accompanying the description where occurrences are listed in full, i.e. the PLANit side of what the
     * entity was to be built between, set beside the GTFS entities it is listed under. Geometry is left out
     * deliberately, being available against these same PLANit ids in the network output
     */
    private static final String PERSISTED_DETAIL =
        "mode %1$s, upstream transfer zone %2$s, downstream transfer zone %3$s";

    /**
     * Context accompanying an entity that existed and was removed again. The log line identifies it by its PLANit id
     * alone, which is what the surrounding log speaks in, whereas a listing outliving the run states its ids in full so
     * that the GTFS entity behind it remains reachable through its external id
     */
    private static final String REMOVED_ENTITY_IDS = "%1$s";
  }

  /** PLANit entity type the issue applies to */
  private final GtfsPlanitEntityType entityType;

  /** what the issue says about the parser */
  private final GtfsIssueDisposition disposition;

  /** where the loss originates */
  private final GtfsIssueOrigin origin;

  /** whether the entity is lost to the issue, which only an entity that existed in the first place can be */
  private final boolean discarding;

  /** how the issue reaches the log */
  private final GtfsIssueLogPolicy logPolicy;

  /** readable description used when reporting */
  private final String description;

  /** entity specific context accompanying the description */
  private final GtfsIssueDetail detail;

  /**
   * Constructor
   *
   * @param entityType PLANit entity type the issue applies to
   * @param disposition what the issue says about the parser
   * @param origin where the loss originates
   * @param discarding whether the entity is lost to the issue
   * @param logPolicy how the issue reaches the log
   * @param description readable description used when reporting
   * @param detailTemplate format of the entity specific context as logged, null when the issue logs none
   * @param persistedDetailTemplate format of the entity specific context as persisted, null to use the logged one
   */
  GtfsPlanitEntityIssue(
      final GtfsPlanitEntityType entityType,
      final GtfsIssueDisposition disposition,
      final GtfsIssueOrigin origin,
      final boolean discarding,
      final GtfsIssueLogPolicy logPolicy,
      final String description,
      final String detailTemplate,
      final String persistedDetailTemplate) {
    this.entityType = entityType;
    this.disposition = disposition;
    this.origin = origin;
    this.discarding = discarding;
    this.logPolicy = logPolicy;
    this.description = description;
    this.detail = GtfsIssueDetail.create(detailTemplate, persistedDetailTemplate);
  }

  /**
   * Collect the PLANit entity type the issue applies to
   *
   * @return entity type
   */
  public GtfsPlanitEntityType getEntityType() {
    return entityType;
  }

  /**
   * Collect where the loss originates
   *
   * @return origin
   */
  public GtfsIssueOrigin getOrigin() {
    return origin;
  }

  /**
   * Verify whether the loss carries over from what the feed side already reported
   *
   * @return true when it originates in the GTFS parsing, false otherwise
   */
  public boolean isKnockOnFromGtfsParsing() {
    return origin == GtfsIssueOrigin.GTFS_PARSING;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getEntityLabel() {
    return entityType.getLabel();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getEntityIdLabel() {
    return entityType.getEntityIdLabel();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDiscarding() {
    return discarding;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GtfsIssueDisposition getDisposition() {
    return disposition;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GtfsIssueLogPolicy getLogPolicy() {
    return logPolicy;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getDescription() {
    return description;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String createDetail(final Object... detailArgs) {
    return detail.createLogged(name(), detailArgs);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String createPersistedDetail(final Object... detailArgs) {
    return detail.createPersisted(name(), detailArgs);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasDetailTemplate() {
    return detail.hasLoggedTemplate();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasPersistedDetailTemplate() {
    return detail.hasPersistedTemplate();
  }
}
