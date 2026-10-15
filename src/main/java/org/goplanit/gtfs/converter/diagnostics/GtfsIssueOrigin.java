package org.goplanit.gtfs.converter.diagnostics;

/**
 * Where the loss an issue reports originates, which decides whether the feed side report already accounts for it.
 *
 * @author markr
 */
public enum GtfsIssueOrigin {

  /**
   * the entity was lost because the GTFS entities behind it were, so what the feed side reported carries over to it.
   * The count is still worth stating, one discarded stop costing as many legs as it was a stop of, but the reason is
   * the one already given there
   */
  GTFS_PARSING,

  /**
   * the entity was lost building the PLANit result or aligning it with the physical network, nothing on the feed side
   * accounting for it
   */
  PLANIT_CONSTRUCTION;
}
