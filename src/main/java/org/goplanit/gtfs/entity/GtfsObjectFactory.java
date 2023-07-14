package org.goplanit.gtfs.entity;

import java.util.logging.Logger;

import org.goplanit.gtfs.enums.GtfsObjectType;

public class GtfsObjectFactory {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsObjectFactory.class.getCanonicalName());

  /** Create a GTFS object of a given type based on the provide object type
   * 
   * @param objectType to base GTFS object on
   * @return created GTFS object
   */
  public static GtfsObject create(GtfsObjectType objectType) {
    try {
      return objectType.value().getConstructor().newInstance();
    }catch(Exception e) {
      LOGGER.severe(String.format("Unable to collect supported keys for %s, likely default constructor is not available for this class",objectType.toString()));
    }
    return null;
    
  }

}
