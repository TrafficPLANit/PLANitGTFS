package org.goplanit.gtfs.entity;

import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * In memory representation of a GTFS entry in shapes.txt
 * 
 * @author markr
 *
 */
public class GtfsShape extends GtfsObject {
  
  /** Supported keys for a GTFS shape instance */
  public static final EnumSet<GtfsKeyType> SUPPORTED_KEYS =
      EnumSet.of(
          GtfsKeyType.SHAPE_ID,
          GtfsKeyType.SHAPE_PT_LAT,
          GtfsKeyType.SHAPE_PT_LON,
          GtfsKeyType.SHAPE_PT_SEQUENCE);

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
  public String getShapeId(){
    return get(GtfsKeyType.SHAPE_ID);
  }  

}
