package org.goplanit.gtfs.converter.diagnostics;

/**
 * Kinds of PLANit entity the converter derives from the feed, and can therefore either fail to build or lose again
 * while the result is brought in line with the physical network.
 *
 * @author markr
 */
public enum GtfsPlanitEntityType {

  /** a stop of a service as it resides on the service network */
  SERVICE_NODE,

  /** a leg between two consecutive stops of a service */
  SERVICE_LEG("GTFS stop pairs"),

  /** a directed leg between two consecutive stops of a service */
  SERVICE_LEG_SEGMENT("GTFS stop pairs"),

  /** a service running the trips of a single GTFS route */
  ROUTED_SERVICE,

  /** the relative schedule a trip of a service adheres to */
  ROUTED_TRIP_SCHEDULE,

  /** a single departure of a scheduled trip */
  DEPARTURE;

  /** what the entity ids recorded against this type denote, null when they are the entity's own ids */
  private final String entityIdLabel;

  /**
   * Constructor for a type identified by its own ids
   */
  GtfsPlanitEntityType() {
    this(null);
  }

  /**
   * Constructor
   *
   * @param entityIdLabel what the entity ids recorded against this type denote, null when they are the entity's own ids
   */
  GtfsPlanitEntityType(final String entityIdLabel) {
    this.entityIdLabel = entityIdLabel;
  }

  /**
   * Collect the type as it reads in a log line or a listing
   *
   * @return label
   */
  public String getLabel() {
    return name().toLowerCase().replace('_', ' ');
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
