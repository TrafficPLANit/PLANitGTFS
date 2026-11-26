package org.goplanit.gtfs.converter.diagnostics;

/**
 * What an issue found while parsing a GTFS feed says about the parser.
 * <p>
 * A count of lost entities alone cannot be acted on: an entity dropped because the run asked for it and one dropped
 * because the parser cannot represent it look the same in a total, yet only the second is work to be done. Separating
 * them turns the same numbers into a backlog.
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
   * An issue declares its disposition for an entity the run meant to keep. An entity ruled out in any respect was not
   * one of those, so its loss is by design whatever issue names it: a parser cannot be faulted for not delivering what
   * it was told to leave out
   * </p>
   * <p>
   * The override runs one way only. Standing within scope does not make a loss a problem, as several issues are by
   * design for entities well inside the area. So scope can lower a declared disposition but never raise it
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
