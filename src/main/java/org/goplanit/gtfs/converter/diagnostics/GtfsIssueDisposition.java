package org.goplanit.gtfs.converter.diagnostics;

/**
 * What an issue encountered while parsing a GTFS feed says about the parser.
 * <p>
 * The distinction exists because a count of lost entities on its own cannot be acted on: an entity dropped because the
 * run asked for it to be dropped and an entity dropped because the parser cannot represent it look identical in a
 * total, yet only the second is work to be done. Separating them turns the same numbers into a backlog.
 * </p>
 *
 * @author markr
 */
public enum GtfsIssueDisposition {

  /** the run asked for this, through its configuration or its spatial and temporal scope */
  BY_DESIGN,

  /** the parser cannot handle this, so it is unavoidable today but unwanted, i.e. a capability gap */
  LIMITATION,

  /** the entity was meant to be usable and was not, i.e. a defect or a data problem rather than a choice */
  PROBLEM;
}
