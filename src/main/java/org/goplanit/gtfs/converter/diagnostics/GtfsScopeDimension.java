package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.gtfs.enums.GtfsObjectType;

import java.util.List;

/**
 * The respects in which a GTFS entity can fall inside or outside what a run covers.
 * <p>
 * A run is narrowed in more than one way and they are not interchangeable. A trip running on another day and a trip
 * running in another city are both outside what was asked for, but for reasons that say entirely different things
 * about the feed, and folding them into a single notion of scope leaves the larger of the two unexplained
 * </p>
 * <p>
 * Which respects apply is a property of the entity type rather than of the entity: a calendar has no location and a
 * stop has no timetable, so asking where a calendar sits is not a hard question but a meaningless one
 * </p>
 *
 * @author markr
 */
public enum GtfsScopeDimension {

  /** where the entity sits relative to the area the run covers */
  SPATIAL,

  /** whether the entity runs on the day and within the time period the run was configured for */
  TEMPORAL,

  /** whether the entity serves a mode activated for the run */
  MODAL,

  /**
   * whether the entity was one the run was asked to include at all, an exclusion naming it rather than describing it.
   * <p>
   * Unlike the others this filters on the entity's identity: a route left out by short name or a stop by id may sit
   * squarely within the area, run on the chosen day and serve an activated mode, and still be none of the run's
   * business. It is never partly so
   * </p>
   */
  SELECTION;

  /**
   * The respects that narrow each kind of entity, in the order they settle as it is parsed.
   * <p>
   * Ordered rather than merely listed because a share is only meaningful against what reached the respect concerned,
   * and what reached it is whatever survived the respects before. Declaring the order here keeps it reviewable
   * instead of leaving it to be inferred from the numbers it produces
   * </p>
   *
   * @param entityType to collect for
   * @return respects in the order they settle, empty where the entity type is narrowed in no respect at all
   */
  public static List<GtfsScopeDimension> getApplicableTo(final GtfsObjectType entityType) {
    switch (entityType) {
      case STOP:
        /* a stop is placed and tested against the exclusions the moment it is read, and only then are the modes it
         * serves considered */
        return List.of(SPATIAL, SELECTION, MODAL);
      case CALENDAR:
        /* a calendar is the day filter itself, so it stands in time and nowhere else */
        return List.of(TEMPORAL);
      case ROUTE:
        /* the exclusions are tested first, then a route's own mode, then the days its trips run, then where they go */
        return List.of(SELECTION, MODAL, TEMPORAL, SPATIAL);
      case TRIP:
      case STOP_TIME:
        /* the day filter comes first, then whatever its route was left out for, then its stops */
        return List.of(TEMPORAL, SELECTION, MODAL, SPATIAL);
      default:
        return List.of();
    }
  }

  /**
   * Verify whether this respect applies to the given entity type at all, as opposed to being a question that cannot
   * be asked of it
   *
   * @param entityType to verify for
   * @return true when it applies, false otherwise
   */
  public boolean appliesTo(final GtfsObjectType entityType) {
    return getApplicableTo(entityType).contains(this);
  }

  /**
   * Collect the name this respect goes by in the report, i.e. its own name suffixed so that a column or entry
   * carrying it is recognisable as a scope rather than as whatever else the report holds
   *
   * @return reported name
   */
  public String getReportedName() {
    return name().toLowerCase() + "_scope";
  }
}
