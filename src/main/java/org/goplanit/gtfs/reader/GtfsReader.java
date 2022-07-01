package org.goplanit.gtfs.reader;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.handler.GtfsFileHandler;
import org.goplanit.gtfs.entity.GtfsObject;
import org.goplanit.gtfs.util.GtfsFileConditions;
import org.goplanit.gtfs.util.GtfsUtils;

/**
 * Top level GTFS reader for one or more GTFS files. The ordering in which the file are read (presuming a handler has been registered
 * for them) is:
 * <ul>
 * <li>/<li>
 * </ul>
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

  /** Read the file of the given file type if a reader is available for it
   * 
   * @param gtfsFileType to reader
   * @param gtfsFileCondition on the file type
   */
  private void read(GtfsFileType gtfsFileType, GtfsFileConditions gtfsFileCondition) {
    if(fileReaders.containsKey(gtfsFileType)) {
      GtfsFileReaderBase fileReader = fileReaders.get(gtfsFileType);
      fileReader.setPresenceCondition(gtfsFileCondition);
      fileReader.read();
    }
  }

  /**
   * Constructor
   * 
   * @param gtfsLocation url of the location of the GTFS files, this should either be a directory containing uncompressed *.txt files or alternatively
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
    
    /* perform reading of files in a logical order, i.e. from less dependencies to more */
    read(GtfsFileType.AGENCIES,       GtfsFileConditions.required() );
    read(GtfsFileType.STOPS,          GtfsFileConditions.required() );
    read(GtfsFileType.ROUTES,         GtfsFileConditions.required() );
    read(GtfsFileType.TRIPS,          GtfsFileConditions.required() );
    read(GtfsFileType.STOP_TIMES,     GtfsFileConditions.required() );
    
    read(GtfsFileType.CALENDARS,      GtfsFileConditions.requiredInAbsenceOf(GtfsFileType.CALENDAR_DATES) ); // technically required if not all are specified in CALENDAR_DATES
    read(GtfsFileType.CALENDAR_DATES, GtfsFileConditions.requiredInAbsenceOf(GtfsFileType.CALENDARS)  );
    read(GtfsFileType.FARE_ATTRIBUTES,GtfsFileConditions.optional() );
    read(GtfsFileType.FARE_RULES,     GtfsFileConditions.optional());
    read(GtfsFileType.SHAPES,         GtfsFileConditions.optional());
    read(GtfsFileType.FREQUENCIES,    GtfsFileConditions.optional());
    read(GtfsFileType.TRANSFERS,      GtfsFileConditions.optional());
    read(GtfsFileType.PATHWAYS,       GtfsFileConditions.optional());
    read(GtfsFileType.LEVELS,         GtfsFileConditions.optional());
    read(GtfsFileType.FEED_INFO,      GtfsFileConditions.requiredinPresenceOf(GtfsFileType.TRANSLATIONS));
    read(GtfsFileType.TRANSLATIONS,   GtfsFileConditions.optional());
    read(GtfsFileType.ATTRIBUTIONS,   GtfsFileConditions.optional());
    
  }
  
  /** Register a handler for a specific file type
   * 
   * @param gtfsFileHandler to register
   * @return the file reader that goes with this handler, it is newly created if no reader existed for the file type, otherwise the existing reader is returned
   */
  public GtfsFileReaderBase addFileHandler(GtfsFileHandler<? extends GtfsObject> gtfsFileHandler) {
    if(gtfsFileHandler==null) {
      LOGGER.warning("Provided GFTSFileHandler is null, cannot be registered on GTFSReader");
      return null;
    }
    
    if(gtfsLocation==null) {
      return null;
    }
    
    /* create file reader if not already available */
    GtfsFileType fileType = gtfsFileHandler.getFileScheme().getFileType();
    GtfsFileReaderBase fileReader = null;
    if(!fileReaders.containsKey(fileType)) {
      fileReader = GtfsReaderFactory.createFileReader(gtfsFileHandler.getFileScheme(), gtfsLocation);
      fileReaders.put(fileType, fileReader);
    }else {
      fileReader = fileReaders.get(fileType);
    }
    
    /* register */
    fileReader.addHandler(gtfsFileHandler);
    return fileReader;
  }
}
