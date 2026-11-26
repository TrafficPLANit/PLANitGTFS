package org.goplanit.gtfs.converter.diagnostics;

/**
 * Where a GTFS entity sits relative to the area the run covers.
 * <p>
 * A feed often covers more ground than the physical network it is mapped onto; a state-wide feed for a single city is
 * the normal case. Measuring the parser against everything in the feed then mostly measures that difference, not the
 * parser. Scope separates the two, so what is reported can be measured against what was ever within the area
 * </p>
 *
 * @author markr
 */
public enum GtfsEntityScope {

  /** entity lies wholly within the area the run covers */
  IN,

  /** entity extends beyond the area the run covers while still reaching into it, only possible for an entity with
   * extent such as a trip or a route */
  PARTIAL,

  /** entity lies wholly beyond the area the run covers */
  OUT,

  /**
   * Scope of the entity is not settled: it was encountered but dropped, or has not yet reached the point where its
   * scope can be told.
   * <p>
   * Internal bookkeeping, not a scope anyone asks for. It makes every entity encountered occupy exactly one cell of the
   * one tally, so what the feed holds, what has a scope and what is within the area are all read off the same counters
   * and cannot disagree. Never reported
   * </p>
   */
  NOT_ESTABLISHED,

  /**
   * The respect does not apply to this kind of entity at all, as a calendar has no location and a stop no timetable.
   * <p>
   * Kept apart from an unsettled scope: one says the question was never answered and might yet be, the other that it
   * cannot be asked. Reported as the same, a reader could not tell a gap in the report from a fact about the entity
   * </p>
   */
  NOT_APPLICABLE;

  /**
   * Verify whether an entity of this scope was ever within the area of the run, which both a wholly and a partly covered
   * entity were
   *
   * @return true when within the area, false otherwise
   */
  public boolean isWithinArea() {
    return this == IN || this == PARTIAL;
  }

  /**
   * Verify whether the scope of the entity was settled at all
   *
   * @return true when established, false otherwise
   */
  public boolean isEstablished() {
    return this == IN || this == PARTIAL || this == OUT;
  }

  /**
   * Verify whether the respect applies to the entity at all
   *
   * @return true when it applies, false otherwise
   */
  public boolean isApplicable() {
    return this != NOT_APPLICABLE;
  }
}
