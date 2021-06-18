package org.planit.gtfs.reader;

import org.planit.gtfs.model.GtfsObject;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.gtfs.enums.GtfsFileType;

/**
 * top level GTFS reader for one or more GTFS files
 * 
 * @author markr
 *
 */
public class GtfsReader {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsReader.class.getCanonicalName());
  
  /** registered file readers based on handlers that are added */
  private final Map<GtfsFileType, GtfsFileReaderBase> fileReaders;

  /**
   * Constructor
   */
  protected GtfsReader() {
    this.fileReaders = new HashMap<GtfsFileType, GtfsFileReaderBase>();
  }
  
  /**
   * Read GTFS files based on the registered file handlers
   */
  public void read() {
    
  }
  
  /** Register a handler for a specific file type
   * 
   * @param gtfsFileHandler to register
   */
  public void addFileHandler(GtfsFileHandler<? extends GtfsObject> gtfsFileHandler) {
    if(gtfsFileHandler==null) {
      LOGGER.warning("Provided GFTSFileHandler is null, cannot be registered on GTFSReader");
      return;
    }
    
    /* create file reader if not already available */
    GtfsFileType fileType = gtfsFileHandler.getFileScheme().getFileType();
    if(!fileReaders.containsKey(fileType)) {
      fileReaders.put(fileType, GtfsReaderFactory.createFileReader(gtfsFileHandler.getFileScheme()));
    }
    
    fileReaders.get(fileType).addHandler(gtfsFileHandler);
    
  }
}
