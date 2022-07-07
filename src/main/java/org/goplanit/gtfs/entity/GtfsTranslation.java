package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in translations.txt
 * 
 * @author markr
 *
 */
public class GtfsTranslation extends GtfsObject {
  
  /** Supported keys for a GTFS translation instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.TABLE_NAME,
          GtfsKeyType.FIELD_NAME,
          GtfsKeyType.LANGUAGE,
          GtfsKeyType.TRANSLATION,
          GtfsKeyType.RECORD_ID,
          GtfsKeyType.RECORD_SUB_ID,
          GtfsKeyType.FIELD_VALUE);

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
    var sb = new StringBuilder("TRANSLATION: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }
}
