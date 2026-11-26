package org.goplanit.gtfs.converter.diagnostics;

/**
 * What an issue does to the entity it names.
 * <p>
 * An entity that is gone and one still there in altered form are read differently: the first is a shortfall to account
 * for, the second is the result taking the shape the network allows. Reported together they would read as if the same
 * thing happened to both, so the effect is declared, not inferred from the counts
 * </p>
 *
 * @author markr
 */
public enum GtfsIssueEffect {

  /** the entity is no longer in the result */
  REMOVAL,

  /** the entity is in the result, altered to fit it */
  MODIFICATION;
}
