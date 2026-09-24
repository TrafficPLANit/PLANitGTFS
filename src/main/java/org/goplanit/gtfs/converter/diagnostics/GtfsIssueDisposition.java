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

  /**
   * Collect the disposition an occurrence carries, given what its issue is declared as and where the entity stood.
   * <p>
   * What an issue declares is the disposition it carries for an entity the run meant to keep. An entity ruled out in
   * any respect was not one of those, and its loss is then by design whatever issue names it, a parser cannot be
   * faulted for failing to deliver what it was told to leave out
   * </p>
   * <p>
   * The override runs one way only. Standing within scope does not make a loss a problem, several issues being
   * deliberate for entities squarely inside the area, so a declared disposition is lowered by scope and never raised
   * by it
   * </p>
   *
   * @param declared disposition of the issue concerned
   * @param scope the entity stood in, may be null where it was never established
   * @return disposition the occurrence carries
   */
  public static GtfsIssueDisposition of(final GtfsIssueDisposition declared, final GtfsEntityScope scope) {
    return scope == GtfsEntityScope.OUT ? BY_DESIGN : declared;
  }
}
