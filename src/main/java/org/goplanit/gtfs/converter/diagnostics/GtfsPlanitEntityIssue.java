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

  /** an endpoint stop of the leg segment has no transfer zone, so the network cannot be reached from it */
  LEG_SEGMENT_STOP_WITHOUT_TRANSFER_ZONE(
      GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, GtfsIssueDisposition.PROBLEM, false, GtfsIssueLogPolicy.COLLATED,
      "Endpoint stop has no transfer zone", Templates.DETAIL, Templates.PERSISTED_DETAIL),

  /** an endpoint of the leg segment has no connectoid granting access to the network for its mode */
  LEG_SEGMENT_ENDPOINT_WITHOUT_ACCESS_CONNECTOID(
      GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, GtfsIssueDisposition.PROBLEM, false, GtfsIssueLogPolicy.COLLATED,
      "Endpoint has no usable access connectoid", Templates.DETAIL, Templates.PERSISTED_DETAIL),

  /**
   * Unable to find available transfer zone access nodes for the leg segment, the GTFS stop likely mapped to an
   * incorrect physical access node upon an earlier path search. Unlike an endpoint without any access connectoid, the
   * transfer zone does have connectoids, just not at the node the stop was mapped to
   */
  LEG_SEGMENT_ENDPOINT_ACCESS_NODE_MISMATCH(
      GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, GtfsIssueDisposition.PROBLEM, false, GtfsIssueLogPolicy.COLLATED,
      "Unable to find available transfer zone access nodes, GTFS stop likely mapped to incorrect physical access node",
      Templates.DETAIL, Templates.PERSISTED_DETAIL),

  /** no physical path exists between the endpoints of the leg segment for its mode */
  LEG_SEGMENT_NO_ELIGIBLE_PHYSICAL_PATH(
      GtfsPlanitEntityType.SERVICE_LEG_SEGMENT, GtfsIssueDisposition.PROBLEM, false, GtfsIssueLogPolicy.COLLATED,
      "No eligible physical path between endpoints", Templates.DETAIL, Templates.PERSISTED_DETAIL),

  /** a service node was removed because it lies beyond the area the physical network covers */
  SERVICE_NODE_OUTSIDE_NETWORK_AREA(
      GtfsPlanitEntityType.SERVICE_NODE, GtfsIssueDisposition.BY_DESIGN, true, GtfsIssueLogPolicy.COLLATED,
      "Service node outside network area", null, Templates.REMOVED_ENTITY_IDS),

  /** a trip schedule was cut back or removed because part of it could not be mapped to the physical network */
  TRIP_SCHEDULE_TRUNCATED_UNMAPPED(
      GtfsPlanitEntityType.ROUTED_TRIP_SCHEDULE, GtfsIssueDisposition.PROBLEM, true, GtfsIssueLogPolicy.COLLATED,
      "Trip schedule truncated, unmapped to physical network", null, Templates.REMOVED_ENTITY_IDS),

  /** several departures of a trip schedule share the same scheduled time */
  DEPARTURE_DUPLICATE_SCHEDULED_TIME(
      GtfsPlanitEntityType.DEPARTURE, GtfsIssueDisposition.PROBLEM, true, GtfsIssueLogPolicy.COLLATED,
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
   * @param discarding whether the entity is lost to the issue
   * @param logPolicy how the issue reaches the log
   * @param description readable description used when reporting
   * @param detailTemplate format of the entity specific context as logged, null when the issue logs none
   * @param persistedDetailTemplate format of the entity specific context as persisted, null to use the logged one
   */
  GtfsPlanitEntityIssue(
      final GtfsPlanitEntityType entityType,
      final GtfsIssueDisposition disposition,
      final boolean discarding,
      final GtfsIssueLogPolicy logPolicy,
      final String description,
      final String detailTemplate,
      final String persistedDetailTemplate) {
    this.entityType = entityType;
    this.disposition = disposition;
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
