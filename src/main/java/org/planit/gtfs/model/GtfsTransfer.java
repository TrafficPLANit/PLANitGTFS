package org.planit.gtfs.model;

import java.util.EnumSet;

import org.planit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in transfers.txt
 * 
 * @author markr
 *
 */
public class GtfsTransfer extends GtfsObject {
  
  /** Supported keys for a GTFS transfer instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.FROM_STOP_ID,
          GtfsKeyType.TO_STOP_ID,
          GtfsKeyType.TRANSFER_TYPE,
          GtfsKeyType.MIN_TRANSFER_TIME);

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
  
}
