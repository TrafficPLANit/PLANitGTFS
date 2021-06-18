package org.planit.gtfs.reader;

import org.planit.gtfs.model.GtfsObject;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.gtfs.GtfsUtils;
import org.planit.gtfs.enums.GtfsFileType;
import org.planit.gtfs.handler.GtfsFileHandler;

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
  
  /** location (dir or zip) of GTFS file(s) */
  private final URL gtfsLocation;

  /**
   * Constructor
   * 
   * @param url of the location of the GTFS files, this should either be a directory containing uncompressed *.txt files or alternatively
   * a *.zip file containing the *.txt files
   */
  protected GtfsReader(final URL gtfsLocation) {
    this.fileReaders = new HashMap<GtfsFileType, GtfsFileReaderBase>();
    
    boolean validGtfsLocation = GtfsUtils.isValidGtfsLocation(gtfsLocation);    
    this.gtfsLocation = validGtfsLocation ? gtfsLocation : null; 
    if(!validGtfsLocation){
      LOGGER.warning(String.format("Provided GTFS location (%s)is neither a directory nor a zip file, unable to instantiate reader", gtfsLocation));
    }
  }
  
  /**
   * Read GTFS files based on the registered file handlers
   */
  public void read() {
    if(gtfsLocation==null) {
      return;
    }
    
    /* TODO: use logical ordering of different file types when we add more types */
    fileReaders.forEach( (type, reader) -> reader.read());
    
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
    
    if(gtfsLocation==null) {
      return;
    }
    
    /* create file reader if not already available */
    GtfsFileType fileType = gtfsFileHandler.getFileScheme().getFileType();
    if(!fileReaders.containsKey(fileType)) {
      fileReaders.put(fileType, GtfsReaderFactory.createFileReader(gtfsFileHandler.getFileScheme(), gtfsLocation));
    }
    
    fileReaders.get(fileType).addHandler(gtfsFileHandler);
    
  }
}
