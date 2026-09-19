package org.goplanit.gtfs.converter.diagnostics;

/**
 * How an issue stands in relation to the scope of the entities it arises for, which is what decides the population its
 * occurrences can be measured against.
 * <p>
 * Two of the three lead to the same denominator, and that is the point of keeping them apart: what the feed holds is
 * used for two quite different reasons, and a reader who cannot tell which cannot tell whether the figure is a
 * limitation of the report or a fact about the parse.
 * </p>
 *
 * @author markr
 */
public enum GtfsIssueScopeRelation {

  /**
   * Arises before its entity type has any scope at all, so the entities it concerns never reached the point where
   * where they sit could be told. A trip dropped for running on another day is the usual case
   */
  PRE_SCOPE,

  /**
   * Arises for entities whatever their scope, those beyond the area the run covers included. Being unconfined to the
   * entities a within area total counts, measuring it against that total reports shares beyond 100%
   */
  ANY_SCOPE,

  /**
   * Arises only for entities that were within the area, which is what makes it a measure of the parser rather than
   * of the ground the feed covers
   */
  WITHIN_AREA;

  /**
   * Verify whether occurrences are to be measured against the entities that were within the area rather than against
   * everything the feed holds
   *
   * @return true when measured against what was within the area, false otherwise
   */
  public boolean isMeasuredAgainstEntitiesWithinArea() {
    return this == WITHIN_AREA;
  }

  /**
   * Collect the relation that holds once an issue known to stand in this relation is also found to stand in the given
   * one, the wider of the two winning since one occurrence beyond the entities within the area is enough to settle it
   *
   * @param other relation also found to hold
   * @return the relation that holds for both
   */
  public GtfsIssueScopeRelation widen(final GtfsIssueScopeRelation other) {
    if (this == ANY_SCOPE || other == ANY_SCOPE) {
      return ANY_SCOPE;
    }
    return this == PRE_SCOPE || other == PRE_SCOPE ? PRE_SCOPE : WITHIN_AREA;
  }
}
