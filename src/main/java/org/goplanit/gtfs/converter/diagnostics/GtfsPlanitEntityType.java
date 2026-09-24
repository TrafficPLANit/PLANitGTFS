package org.goplanit.gtfs.converter.diagnostics;

/**
 * Kinds of PLANit entity the converter derives from the feed, and can therefore either fail to build or lose again
 * while the result is brought in line with the physical network.
 *
 * @author markr
 */
public enum GtfsPlanitEntityType {

  /** a zone a stop is boarded from, whether the feed brought it about or it was already in the zoning */
  TRANSFER_ZONE(GtfsPlanitComponent.ZONING),

  /** a grouping of transfer zones that belong together, such as the platforms of a single station */
  TRANSFER_ZONE_GROUP(GtfsPlanitComponent.ZONING),

  /** the transfer zone a GTFS stop is boarded from, whether created for it or already serving another stop */
  STOP_TRANSFER_ZONE_MAPPING(
      GtfsPlanitComponent.ZONING, "GTFS stops", "GTFS stop to transfer zone mapping"),

  /** an access point granting a transfer zone entry to the physical network */
  CONNECTOID(GtfsPlanitComponent.ZONING),

  /** a stop of a service as it resides on the service network */
  SERVICE_NODE(GtfsPlanitComponent.SERVICE_NETWORK),

  /** a leg between two consecutive stops of a service */
  SERVICE_LEG(GtfsPlanitComponent.SERVICE_NETWORK, "GTFS stop pairs"),

  /** a directed leg between two consecutive stops of a service */
  SERVICE_LEG_SEGMENT(GtfsPlanitComponent.SERVICE_NETWORK, "GTFS stop pairs"),

  /** a service running the trips of a single GTFS route */
  ROUTED_SERVICE(GtfsPlanitComponent.ROUTED_SERVICES),

  /** the relative schedule a trip of a service adheres to */
  ROUTED_TRIP_SCHEDULE(GtfsPlanitComponent.ROUTED_SERVICES),

  /** a single departure of a scheduled trip */
  DEPARTURE(GtfsPlanitComponent.ROUTED_SERVICES);

  /** the PLANit component the entity ends up in */
  private final GtfsPlanitComponent component;

  /** what the entity ids recorded against this type denote, null when they are the entity's own ids */
  private final String entityIdLabel;

  /** how the type reads where its name does not do, null when the name does */
  private final String label;

  /**
   * Constructor for a type identified by its own ids
   *
   * @param component the entity ends up in
   */
  GtfsPlanitEntityType(final GtfsPlanitComponent component) {
    this(component, null);
  }

  /**
   * Constructor
   *
   * @param component the entity ends up in
   * @param entityIdLabel what the entity ids recorded against this type denote, null when they are the entity's own ids
   */
  GtfsPlanitEntityType(final GtfsPlanitComponent component, final String entityIdLabel) {
    this(component, entityIdLabel, null);
  }

  /**
   * Constructor
   *
   * @param component the entity ends up in
   * @param entityIdLabel what the entity ids recorded against this type denote, null when they are the entity's own ids
   * @param label how the type reads where its name does not do, null when the name does
   */
  GtfsPlanitEntityType(
      final GtfsPlanitComponent component, final String entityIdLabel, final String label) {
    this.component = component;
    this.entityIdLabel = entityIdLabel;
    this.label = label;
  }

  /**
   * Collect the PLANit component the entity ends up in
   *
   * @return component
   */
  public GtfsPlanitComponent getComponent() {
    return component;
  }

  /**
   * Collect the type as it reads in a log line or a listing
   *
   * @return label
   */
  public String getLabel() {
    return label != null ? label : name().toLowerCase().replace('_', ' ');
  }

  /**
   * Collect the type this type subdivides, its entities each being counted against one entity of that type rather
   * than standing on their own, so that it is reported underneath it
   *
   * @return type nested under, null when the type stands on its own
   */
  public GtfsPlanitEntityType getNestedUnder() {
    return this == STOP_TRANSFER_ZONE_MAPPING ? TRANSFER_ZONE : null;
  }

  /**
   * Collect what the entity ids recorded against this type denote, needed only where the entity has no id of its own
   * to be reported under and is identified by the GTFS entities it was to be built from instead
   *
   * @return entity id label, null when the ids need no clarification
   */
  public String getEntityIdLabel() {
    return entityIdLabel;
  }
}
