package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in feed_info.txt
 * 
 * @author markr
 *
 */
public class GtfsFeedInfo extends GtfsObject {
  
  /** Supported keys for a GTFS feed information instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.FEED_PUBLISHER_NAME,
          GtfsKeyType.FEED_PUBLISHER_URL,
          GtfsKeyType.FEED_LANG,
          GtfsKeyType.DEFAULT_LANG,
          GtfsKeyType.FEED_START_DATE,
          GtfsKeyType.FEED_END_DATE,
          GtfsKeyType.FEED_VERSION,
          GtfsKeyType.FEED_CONTACT_EMAIL,
          GtfsKeyType.FEED_CONTACT_URL);  

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }

  /**
   * String of all key value pairs of this GTFS entity
   * @return created string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("FEED_INFO: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }
}
