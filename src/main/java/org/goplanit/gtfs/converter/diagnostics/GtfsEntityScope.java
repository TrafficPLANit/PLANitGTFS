package org.goplanit.gtfs.converter.diagnostics;

/**
 * Where a GTFS entity sits relative to the area the run covers.
 * <p>
 * A feed routinely covers more ground than the physical network it is being mapped onto, a state-wide feed against a
 * single city being the normal case. Measuring what the parser achieved against everything in the feed therefore
 * mostly measures that difference, not the parser. Scope separates the two, so that what is reported can be measured
 * against what was ever in reach
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
  OUT;

  /**
   * Verify whether an entity of this scope was ever in reach of the run, which both a wholly and a partly covered
   * entity were
   *
   * @return true when in reach, false otherwise
   */
  public boolean isInReach() {
    return this != OUT;
  }
}
