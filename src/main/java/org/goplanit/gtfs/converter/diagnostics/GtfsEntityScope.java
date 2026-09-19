package org.goplanit.gtfs.converter.diagnostics;

/**
 * Where a GTFS entity sits relative to the area the run covers.
 * <p>
 * A feed routinely covers more ground than the physical network it is being mapped onto, a state-wide feed against a
 * single city being the normal case. Measuring what the parser achieved against everything in the feed therefore
 * mostly measures that difference, not the parser. Scope separates the two, so that what is reported can be measured
 * against what was ever within the area
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
   * Scope of the entity is not settled, it having been encountered but dropped, or not yet reached the point where
   * where it sits could be told.
   * <p>
   * Internal bookkeeping rather than a scope anyone asks for. It exists so that every entity encountered occupies
   * exactly one cell of the one tally, which is what lets what the feed holds, what has a scope and what is within the area
   * all be read off the same counters without any of them being able to disagree. Never reported
   * </p>
   */
  NOT_ESTABLISHED;

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
    return this != NOT_ESTABLISHED;
  }
}
