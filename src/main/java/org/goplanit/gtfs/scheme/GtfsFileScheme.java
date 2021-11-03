package org.goplanit.gtfs.scheme;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsObjectType;

/**
 * Each GTFS file scheme represents on particular GTFS file and its mapping to an in memory GTFS object
 * 
 * @author markr
 *
 */
public class GtfsFileScheme{

  private final GtfsFileType fileType;
  
  private final GtfsObjectType objectType;
  
  /** Constructor
   * 
   * @param fileType of this file scheme
   * @param objectType that goes with entries of this file scheme
   */
  protected GtfsFileScheme(final GtfsFileType fileType, final GtfsObjectType objectType) {
    this.fileType = fileType;
    this.objectType = objectType;
  }

  public GtfsFileType getFileType() {
    return fileType;
  }

  public GtfsObjectType getObjectType() {
    return objectType;
  }

  /**
   * equals when both enums are identical
   */
  @Override
  public boolean equals(Object o) {
    if(o==null || !(o instanceof GtfsFileScheme)) {
      return false;
    }
    
    GtfsFileScheme other = (GtfsFileScheme)o;
    return other.getFileType().equals(getFileType()) && 
        other.getObjectType().equals(getObjectType());
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return String.format("file scheme - file-type: %s - object type: %s", fileType.toString(), objectType.toString());
  }
}
