package org.planit.gtfs.enums;

/**
 * Supported GTFS file types and their corresponding (default) file name in which entries are expected to be found
 * 
 * @author markr
 *
 */
public enum GtfsFileType {
  
  TRIPS("trips.text");

  private final String value;
  
  /** Create a GTFS file type
   * 
   * @param value to use
   */
  private GtfsFileType(String value){
    this.value = value;
  }
  
  /** Get the value of the enum
   * 
   * @return value of the enum
   */
  public String value() {
    return value;
  }
  
}
