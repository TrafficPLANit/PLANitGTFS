package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in levels.txt
 * 
 * @author markr
 *
 */
public class GtfsLevel extends GtfsObject {
  
  /** Supported keys for a GTFS levels instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.LEVEL_ID,
          GtfsKeyType.LEVEL_INDEX,
          GtfsKeyType.LEVEL_NAME);

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
  
  /** Get the level id
   * 
   * @return level id
   */
  public String getLevelId(){
    return get(GtfsKeyType.LEVEL_ID);
  }

  /**
   * String of all key value pairs of this GTFS entity
   * @return created string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("LEVEL: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }
}
