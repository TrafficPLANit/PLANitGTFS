package org.goplanit.gtfs.converter.diagnostics;

/**
 * What an issue does to the entity it names.
 * <p>
 * An entity that is gone and an entity that is still there in altered form are read differently: the first is a
 * shortfall to account for, the second is the result taking the shape the network allows. Reported together they
 * read as though the same thing befell both, so the distinction is declared rather than inferred from the counts
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
