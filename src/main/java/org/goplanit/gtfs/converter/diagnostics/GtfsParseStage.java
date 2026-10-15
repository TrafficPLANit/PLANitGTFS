package org.goplanit.gtfs.converter.diagnostics;

/**
 * The stage of the GTFS conversion an entity was lost or flagged in. Each stage consumes the output of the previous
 * one, so the stage an issue is attributed to narrows down where to look for its cause.
 * <p>
 * Only the stages that read the feed appear here. What happens to the result afterwards, when it is mapped onto the
 * physical network and pruned to fit, is no longer about GTFS entities but about the PLANit entities derived from
 * them, and is accounted for as such
 * </p>
 *
 * @author markr
 */
public enum GtfsParseStage {

  /** parsing of routes, trips, stop times and calendars into a service network and routed services */
  SERVICES,

  /** parsing of GTFS stops into transfer zones and connectoids on the physical network */
  STOP;
}
