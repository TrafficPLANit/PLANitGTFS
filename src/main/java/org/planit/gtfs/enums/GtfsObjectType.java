package org.planit.gtfs.enums;

import org.planit.gtfs.model.GtfsObject;
import org.planit.gtfs.model.GtfsTrip;

/**
 * The available supported object types and their corresponding class
 * 
 * @author markr
 *
 */
public enum GtfsObjectType {

  TRIP(GtfsTrip.class);

  private final Class<? extends GtfsObject> value;
  
  /** Create a GTFS file type
   * 
   * @param value to use
   */
  private GtfsObjectType(Class<? extends GtfsObject> value){
    this.value = value;
  }
  
  /** Get the value of the enum
   * 
   * @return value of the enum
   */
  public Class<? extends GtfsObject> value() {
    return value;
  }
}
