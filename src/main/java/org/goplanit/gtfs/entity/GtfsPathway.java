package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in pathways.txt
 * 
 * @author markr
 *
 */
public class GtfsPathway extends GtfsObject {
  
  /** Supported keys for a GTFS shape instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.PATHWAY_ID,
          GtfsKeyType.FROM_STOP_ID,
          GtfsKeyType.TO_STOP_ID,
          GtfsKeyType.PATHWAY_MODE,
          GtfsKeyType.LENGTH,
          GtfsKeyType.TRAVERSAL_TIME,
          GtfsKeyType.STAIR_COUNT,
          GtfsKeyType.MAX_SLOPE,
          GtfsKeyType.MIN_WIDTH,
          GtfsKeyType.SIGNPOSTED_AS,
          GtfsKeyType.REVERSE_SIGNPOSTED_AS,
          GtfsKeyType.IS_BIDIRECTIONAL);

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<GtfsKeyType> getSupportedKeys() {
    return SUPPORTED_KEYS;
  }
  
  /** Get the shape id
   * 
   * @return shape id
   */
  public String getPathwayId(){
    return get(GtfsKeyType.PATHWAY_ID);
  }

  /**
   * String of all key value pairs of this GTFS entity
   * @return created string
   */
  @Override
  public String toString(){
    var sb = new StringBuilder("PATHWAY: ");
    super.appendKeyValues(sb);
    return sb.toString();
  }
}
