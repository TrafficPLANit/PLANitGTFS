package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

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

  /**
   * String of all key value pairs of this GTFS entity
   * @return created string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("TRANSFER: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }
}
