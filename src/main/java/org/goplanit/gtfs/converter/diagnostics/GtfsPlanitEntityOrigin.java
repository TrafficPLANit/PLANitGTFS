package org.goplanit.gtfs.converter.diagnostics;

import java.util.Arrays;

/**
 * How a PLANit entity the converter records came about, being either an entity created for the feed or a pre-existing
 * one it was attached to, in which case it records the rule that identified the match.
 *
 * @author markr
 */
public enum GtfsPlanitEntityOrigin {

  /** no pre-existing zone was found for the stop, so the entity was created for it */
  CREATED_FOR_STOP("Newly created"),

  /** a pre-existing zone carries the stop's platform code or name, which is the most trustworthy match there is */
  MATCHED_ON_PLATFORM_NAME("Matched to pre-existing transfer zone on platform code/name"),

  /** a pre-existing zone is accessed from the very link segments the stop would have been given */
  MATCHED_ON_ACCESS_LINK_SEGMENT("Matched to pre-existing transfer zone on access link segment"),

  /** a pre-existing zone lies closest to the stop and reaches the road at an acceptable angle to the way the stop does */
  MATCHED_ON_CLOSEST_ACCEPTABLE_ANGLE("Matched to pre-existing transfer zone on proximity and access angle"),

  /** settings pin the stop to a pre-existing zone, so no rule was applied at all */
  MAPPED_BY_SETTINGS("Mapped to pre-existing transfer zone by settings"),

  /** added after the stops were read so that the zones they brought about can be reached for access and egress */
  INJECTED_FOR_ACCESS_EGRESS("Injected for access and egress"),

  /** a stop sits partway along a link, which is split there so that it has a node to be reached from */
  BROKEN_OPEN_FOR_STOP_ACCESS("Added where a pre-existing link was broken open to reach a stop");

  /** readable description used when reporting */
  private final String description;

  /**
   * Constructor
   *
   * @param description readable description used when reporting
   */
  GtfsPlanitEntityOrigin(final String description) {
    this.description = description;
  }

  /**
   * Collect the subdivision the entities of this origin are reported under
   *
   * @return subtype
   */
  public String getSubType() {
    return name();
  }

  /**
   * Collect the origin as it reads in a log line
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Collect the origin going by the given name
   *
   * @param name to find for
   * @return origin, null when the name is not one
   */
  public static GtfsPlanitEntityOrigin findByName(final String name) {
    return Arrays.stream(values()).filter(origin -> origin.name().equals(name)).findFirst().orElse(null);
  }
}
