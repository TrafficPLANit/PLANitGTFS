package org.goplanit.gtfs.enums;

/**
 * Defines the different Route Type choices:
 * <ul>
 *   <li>DEFAULT - use default route type options as specified in original GTFS feed</li>
 *   <li>EXTENDED - use the extended options as per https://developers.google.com/transit/gtfs/reference/extended-route-types</li>
 * </ul>
 *
 * @author markr
 */
public enum RouteTypeChoice {
  ORIGINAL,
  EXTENDED;
}
