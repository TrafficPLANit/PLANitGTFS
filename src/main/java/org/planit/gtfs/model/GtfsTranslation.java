package org.planit.gtfs.model;

import java.util.EnumSet;

import org.planit.gtfs.enums.GtfsKeyType;

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
  
}
