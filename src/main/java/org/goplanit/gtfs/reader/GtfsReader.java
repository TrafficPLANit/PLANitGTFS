package org.goplanit.gtfs.reader;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.gtfs.enums.GtfsColumnType;
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

  /** base column configuration to apply across all readers */
  private final GtfsColumnType gtfsColumnConfiguration;
  
  /** location (dir or zip) of GTFS file(s) */
  private final URL gtfsLocation;

  /** Read the file of the given file type if a reader is available for it
   * 
   * @param gtfsFileType to reader
   * @param gtfsFileCondition on the file type
   * @param charSet to use for the reader
   */
  private void read(GtfsFileType gtfsFileType, GtfsFileConditions gtfsFileCondition, Charset charSet) {
    if(fileReaders.containsKey(gtfsFileType)) {
      GtfsFileReaderBase fileReader = fileReaders.get(gtfsFileType);
      fileReader.setPresenceCondition(gtfsFileCondition);
      fileReader.read(charSet);
    }
  }

  /**
   * Constructor
   * 
   * @param gtfsLocation url of the location of the GTFS files, this should either be a directory containing uncompressed *.txt files or alternatively
   * a *.zip file containing the *.txt files
   * @param gtfsColumnConfiguration initial column configuration for all created readers/handlers
   */
  protected GtfsReader(final URL gtfsLocation, GtfsColumnType gtfsColumnConfiguration) {
    this.fileReaders = new HashMap<>();
    this.gtfsColumnConfiguration = gtfsColumnConfiguration;
    
    boolean validGtfsLocation = GtfsUtils.isValidGtfsLocation(gtfsLocation);    
    this.gtfsLocation = validGtfsLocation ? gtfsLocation : null; 
    if(!validGtfsLocation){
      LOGGER.warning(String.format("Provided GTFS location (%s)is neither a directory nor a zip file, unable to instantiate reader", gtfsLocation));
    }
  }
  
  /**
   * Read GTFS files based on the registered file handlers
   *
   * @param charSet to use for reading
   */
  public void read(Charset charSet) {
    if(gtfsLocation==null) {
      return;
    }
    
    /* perform reading of files in a logical order, i.e. from less dependencies to more */
    read(GtfsFileType.AGENCIES,       GtfsFileConditions.required(), charSet );
    read(GtfsFileType.STOPS,          GtfsFileConditions.required(), charSet );
    read(GtfsFileType.ROUTES,         GtfsFileConditions.required(), charSet );
    read(GtfsFileType.TRIPS,          GtfsFileConditions.required(), charSet );
    read(GtfsFileType.STOP_TIMES,     GtfsFileConditions.required(), charSet );
    
    read(GtfsFileType.CALENDARS,      GtfsFileConditions.requiredInAbsenceOf(GtfsFileType.CALENDAR_DATES), charSet ); // technically required if not all are specified in CALENDAR_DATES
    read(GtfsFileType.CALENDAR_DATES, GtfsFileConditions.requiredInAbsenceOf(GtfsFileType.CALENDARS), charSet  );
    read(GtfsFileType.FARE_ATTRIBUTES,GtfsFileConditions.optional(), charSet );
    read(GtfsFileType.FARE_RULES,     GtfsFileConditions.optional(), charSet);
    read(GtfsFileType.SHAPES,         GtfsFileConditions.optional(), charSet);
    read(GtfsFileType.FREQUENCIES,    GtfsFileConditions.optional(), charSet);
    read(GtfsFileType.TRANSFERS,      GtfsFileConditions.optional(), charSet);
    read(GtfsFileType.PATHWAYS,       GtfsFileConditions.optional(), charSet);
    read(GtfsFileType.LEVELS,         GtfsFileConditions.optional(), charSet);
    read(GtfsFileType.FEED_INFO,      GtfsFileConditions.requiredInPresenceOf(GtfsFileType.TRANSLATIONS), charSet);
    read(GtfsFileType.TRANSLATIONS,   GtfsFileConditions.optional(), charSet);
    read(GtfsFileType.ATTRIBUTIONS,   GtfsFileConditions.optional(), charSet);
    
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
      fileReader = GtfsReaderFactory.createFileReader(gtfsFileHandler.getFileScheme(), gtfsLocation, gtfsColumnConfiguration);
      fileReaders.put(fileType, fileReader);
    }else {
      fileReader = fileReaders.get(fileType);
    }
    
    /* register */
    fileReader.addHandler(gtfsFileHandler);
    return fileReader;
  }
}
