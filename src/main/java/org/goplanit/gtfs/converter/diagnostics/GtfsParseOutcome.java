package org.goplanit.gtfs.converter.diagnostics;

/**
 * What became of a GTFS entity the converter encountered. An entity reaches exactly one outcome, so the outcomes of
 * all entities of a type sum to the number of entities of that type seen in the feed.
 *
 * @author markr
 */
public enum GtfsParseOutcome {

  /** accepted and represented in the memory model, possibly while carrying an issue */
  PARSED,

  /** lost, with the issue responsible recorded alongside it */
  DISCARDED;

  /**
   * Collect the outcome as it appears in persisted output
   *
   * @return outcome in lower case
   */
  public String getLabel() {
    return name().toLowerCase();
  }
}
