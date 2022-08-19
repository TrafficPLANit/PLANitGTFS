package org.goplanit.gtfs.handler;

import org.goplanit.gtfs.entity.GtfsObject;
import org.goplanit.gtfs.scheme.GtfsFileScheme;

/**
 * A file handler base class to handle callbacks for a particular GTFS file type
 * 
 * @param <T> GTFS objct type this handler supports
 *  
 * @author markr
 *
 */
public abstract class GtfsFileHandler<T extends GtfsObject> {
  
  /** file scheme containing the information regarding what GTFS file is supported */
  private final GtfsFileScheme fileScheme;

  /** Constructor 
   * 
   * @param fileScheme supported by this handler
   */
  protected GtfsFileHandler(GtfsFileScheme fileScheme) {
    this.fileScheme = fileScheme;
  }
  
  /** Handle raw GTFS object, cast to T and then delegate to handle
   * 
   * @param gtfsObject to handle
   */
  @SuppressWarnings("unchecked")
  public void handleRaw(GtfsObject gtfsObject) {
    handle((T)gtfsObject);
  }
  
  /** Handle GTFS object of type T
   * 
   * @param gtfsObject to handler
   */
  public abstract void handle(T gtfsObject);

  /** Verify if handler is compatible with given file scheme
   * 
   * @param otherFileScheme to compare to
   * @return true when compatible, false otherwise
   */
  public boolean isCompatible(GtfsFileScheme otherFileScheme) {
    if(otherFileScheme == null) {
      return false;
    }
    return otherFileScheme.equals(fileScheme);
  }

  /** File scheme supported by this handler
   * 
   * @return file scheme
   */
  public final GtfsFileScheme getFileScheme() {
    return fileScheme;
  }


  /**
   * Reset the handler
   */
  public void reset(){}
}
