package org.goplanit.gtfs.converter.diagnostics;

/**
 * How an issue reaches the log, which is governed by the volume it arrives in rather than by how serious it is.
 * <p>
 * An issue affecting millions of entities and an issue affecting one both matter, but printing a line per entity for
 * the former buries the latter. The disposition states what an issue means; this states how often it may speak.
 * </p>
 *
 * @author markr
 */
public enum GtfsIssueLogPolicy {

  /**
   * Counted only: no line in the summary, no sample, and no entry in the persisted listing. For issues whose total is
   * worth having, so the entities concerned are accounted for, but whose individual cases say nothing, such as feed
   * entities far outside the area asked for. Listing those would cost memory and push out the retained cases of issues
   * worth reading
   */
  SILENT_COUNT_ONLY,

  /**
   * Reported once at the end of the parse, as a total with a sample of the entities concerned. The default, and what
   * the bulk of issues use however often they occur
   */
  COLLATED,

  /**
   * Logged at the moment it arises, in addition to being reported in the summary. Reserved for issues that should not
   * occur at all, where the surrounding log context is worth more than the tidiness of a collapsed line
   */
  IMMEDIATE;
}
