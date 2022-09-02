package org.goplanit.gtfs.entity;

import java.util.EnumMap;
import java.util.EnumSet;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * Base class for any GTFS memory model object with key value pairs for the data
 * 
 * @author markr
 *
 */
public abstract class GtfsObject {

  /** track key value pairs */
  EnumMap<GtfsKeyType, String> keyValueMap = new EnumMap<>(GtfsKeyType.class);

  /**
   * Append values to provided string builder
   * @param sb to append to
   */
  protected void appendKeyValues(StringBuilder sb) {
    keyValueMap.forEach( (k,v) -> {
      sb.append(k.value());
      sb.append(" ");
      sb.append(v==null ? "n/a" : v);
      sb.append(", ");
    });
    sb.deleteCharAt(sb.length()-1);
  }
  
  public String get(GtfsKeyType key) {
    return keyValueMap.get(key);
  }
  
  public String put(GtfsKeyType key, String value) {
    return keyValueMap.put(key, value);
  }  
  
  public boolean containsKey(GtfsKeyType key) {
    return keyValueMap.containsKey(key);
  }  
  
  /** All supported keys for this GTFS object
   * 
   * @return supported keys
   */
  public abstract EnumSet<GtfsKeyType> getSupportedKeys();


}
