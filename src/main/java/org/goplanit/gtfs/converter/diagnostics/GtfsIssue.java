package org.goplanit.gtfs.converter.diagnostics;

/**
 * What every issue the converter records has in common, irrespective of whether it concerns an entity the feed supplied
 * or one the converter set out to build from it.
 * <p>
 * Reporting an issue depends on none of that distinction: collating it, sampling the entities it concerns, composing
 * its context and writing it to a listing are the same work either way. What it is expressed here so that it is
 * implemented once, and what differs between the two kinds of issue stays with the issues themselves.
 * </p>
 *
 * @author markr
 */
public interface GtfsIssue {

  /**
   * Collect the name identifying the issue, which doubles as the key it is collated under
   *
   * @return name
   */
  String name();

  /**
   * Collect the readable description used when reporting
   *
   * @return description
   */
  String getDescription();

  /**
   * Collect what the issue says about the parser
   *
   * @return disposition
   */
  GtfsIssueDisposition getDisposition();

  /**
   * Collect how the issue reaches the log
   *
   * @return log policy
   */
  GtfsIssueLogPolicy getLogPolicy();

  /**
   * Collect the kind of entity the issue applies to, as it reads in a log line or a listing
   *
   * @return entity label
   */
  String getEntityLabel();

  /**
   * Compose the entity specific context accompanying this issue from the arguments its template expects
   *
   * @param detailArgs to compose from, none when the issue takes no context
   * @return composed detail, null when the issue takes no context
   */
  String createDetail(Object... detailArgs);

  /**
   * Compose the entity specific context accompanying this issue when persisted, from the same arguments its logged
   * context is composed from
   *
   * @param detailArgs to compose from, none when the issue takes no context
   * @return composed detail, null when the issue takes no context
   */
  String createPersistedDetail(Object... detailArgs);

  /**
   * Verify whether the issue accompanies its description with entity specific context
   *
   * @return true when it does, false otherwise
   */
  boolean hasDetailTemplate();

  /**
   * Verify whether the issue accompanies its description with entity specific context when persisted
   *
   * @return true when it does, false otherwise
   */
  boolean hasPersistedDetailTemplate();

  /**
   * Verify whether the issue costs the entity it is registered against.
   * <p>
   * Only meaningful where the entity exists independently of the issue, as a GTFS entity read from the feed does. An
   * entity the converter set out to build and could not simply is not there, so nothing is discarded
   * </p>
   *
   * @return true when the entity is lost to the issue, false otherwise
   */
  default boolean isDiscarding() {
    return false;
  }

  /**
   * Collect what the entity ids recorded against this issue denote, stated once ahead of the sample listing them.
   * <p>
   * Needed only where those ids are not the ids of the entity the issue is reported under, as is the case for an entity
   * the converter derives rather than reads, which has no id of its own to be sampled by and is identified by the GTFS
   * entities it was to be built from instead
   * </p>
   *
   * @return entity id label, null when the ids need no clarification
   */
  default String getEntityIdLabel() {
    return null;
  }

  /**
   * Verify whether the entity ids recorded against this issue need clarifying
   *
   * @return true when they do, false otherwise
   */
  default boolean hasEntityIdLabel() {
    return getEntityIdLabel() != null;
  }
}
