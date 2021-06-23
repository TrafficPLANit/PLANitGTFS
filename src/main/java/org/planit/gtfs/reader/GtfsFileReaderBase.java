package org.planit.gtfs.reader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.planit.gtfs.enums.GtfsKeyType;
import org.planit.gtfs.handler.GtfsFileHandler;
import org.planit.gtfs.model.GtfsObject;
import org.planit.gtfs.model.GtfsObjectFactory;
import org.planit.gtfs.scheme.GtfsFileScheme;
import org.planit.gtfs.util.GtfsFileConditions;
import org.planit.gtfs.util.GtfsUtils;

/**
 * A GTFS file reader containing generic code for any GTFS file
 * 
 * @author markr
 *
 */
public class GtfsFileReaderBase {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsFileReaderBase.class.getCanonicalName());
  
  /** file scheme containing the information regarding what GTFS file to parse and how */
  private final GtfsFileScheme fileScheme;
  
  /** location (dir or zip) of GTFS file(s) */
  private final URL gtfsLocation;  
  
  /** registered handlers to use for each entry parsed */
  private final Set<GtfsFileHandler<? extends GtfsObject>> handlers;
  
  /** user configurable settings */
  private final GtfsFileReaderSettings settings;
  
  /** conditions regarding the presence of this file */
  private GtfsFileConditions filePresenceCondition; 
  
  /** Validate header map against supported keys for this file
   * 
   * @param headerMap to validate
   * @return true when all header entries are supported, false otherwise
   */
  private boolean isValid(Map<String, Integer> headerMap) {
    EnumSet<GtfsKeyType> supportedKeys = GtfsUtils.getSupportedKeys(fileScheme.getObjectType());
    for(String header : headerMap.keySet()) {
      if(!GtfsKeyType.valueIn(supportedKeys, header.trim().toLowerCase())) {
        return false;
      }
    }
    return true;
  }  

  /** Map the headers in the file to the correct GTFS keys. Since the headers might have spaces or non-lowercase characters we preserve the 
   * actual parsed header as key but account for these anomalies when finding the appropriate key that goes with it. 
   * 
   * @param headerMap to create GtfsKey mapping for
   * @return created mapping
   */
  private Map<String, GtfsKeyType> mapHeadersToGtfsKeys(Map<String, Integer> headerMap) {
    Map<String, GtfsKeyType> headerToKeyMap = new HashMap<String, GtfsKeyType>();
    for(String header : headerMap.keySet()) {
      GtfsKeyType.fromValue(header.trim().toLowerCase()).ifPresent( key -> headerToKeyMap.put(header, key));
    }
    return headerToKeyMap;
  }

  /** Create a copy of passed in columns but without any columns that are marked for exclusion.
   * 
   * @param gtfsFileColumns to filter
   * @return gtfsFileColumns without excluded columns
   */
  private Map<String, GtfsKeyType> filterExcludedColumns(final Map<String, GtfsKeyType> gtfsFileColumns) {
    Map<String, GtfsKeyType> filteredColumns = new HashMap<String, GtfsKeyType>(gtfsFileColumns);
    Iterator<GtfsKeyType> columnIter = filteredColumns.values().iterator();
    while(columnIter.hasNext()) {
      GtfsKeyType column = columnIter.next();
      if(getSettings().isExcludedColumn(column)) {
        columnIter.remove();
      }      
    }
    return filteredColumns;
  }

  /** Parse entries for given parser
   * 
   * @param csvParser to use
   * @param columnsToParse to use
   */
  private void parseGtfsRecords(final CSVParser csvParser, final Map<String, GtfsKeyType> columnsToParse) {
    Iterator<CSVRecord> entryIterator = csvParser.iterator();
    while(entryIterator.hasNext()) {
      
      CSVRecord gtfsEntryRecord = entryIterator.next();
      GtfsObject gtfsObject = GtfsObjectFactory.create(fileScheme.getObjectType());
      
      /* populate */
      for(Entry<String, GtfsKeyType> entry : columnsToParse.entrySet()) {
        final GtfsKeyType key = entry.getValue();
        final String value = gtfsEntryRecord.get(entry.getKey());
        gtfsObject.put(key, value);
      }
      
      /* delegate to handler */
      for(GtfsFileHandler<? extends GtfsObject> handler : handlers) {
        handler.handleRaw(gtfsObject);
      }
    }
  }

  /** Explicitly indicate the expectations regarding the presence of this file. When marked as optional no warnings will be logged
   * when it is not present.
   *  
   * @param filePResenceConditions to use
   */
  protected void setPresenceCondition(GtfsFileConditions filePresenceCondition) {
    this.filePresenceCondition = filePresenceCondition;
  }
  
  /** Constructor using default gtfs reader settings
   * 
   * @param fileScheme the file scheme this file reader is based on
   * @param gtfsLocation to base file location to parse from on (dir or zip file)
   */
  protected GtfsFileReaderBase(final GtfsFileScheme fileScheme, URL gtfsLocation) {
    this(fileScheme, gtfsLocation, new GtfsFileReaderSettings());
  }  

  /** Constructor
   * 
   * @param fileScheme the file scheme this file reader is based on
   * @param gtfsLocation to base file location to parse from on (dir or zip file)
   * @param settings to use
   */
  protected GtfsFileReaderBase(final GtfsFileScheme fileScheme, URL gtfsLocation, GtfsFileReaderSettings settings) {
    this.fileScheme = fileScheme;
    this.settings = settings;
    this.filePresenceCondition = null;    
    
    this.handlers = new HashSet<GtfsFileHandler<? extends GtfsObject>>();
    
    boolean validGtfsLocation = GtfsUtils.isValidGtfsLocation(gtfsLocation);    
    this.gtfsLocation = validGtfsLocation ? gtfsLocation : null; 
    if(!validGtfsLocation){
      LOGGER.warning(String.format("Provided GTFS location (%s)is neither a directory nor a zip file, unable to instantiate file reader", gtfsLocation));
    }    
  }
  
  /**
   * Perform the reading of the file
   */
  public void read() {
            
    try (InputStream gtfsInputStream = GtfsUtils.createInputStream(gtfsLocation, fileScheme, filePresenceCondition);){
      if(gtfsInputStream!=null) {
        Reader gtfsInputReader = new InputStreamReader(gtfsInputStream);
        CSVParser csvParser = new CSVParser(gtfsInputReader, CSVFormat.DEFAULT.withHeader());
        Map<String, Integer> headerMap = csvParser.getHeaderMap();
        if(!isValid(headerMap)) {
          
          LOGGER.warning(String.format("Invalid header for %s - %s, ignore file", gtfsLocation.toString(), fileScheme.getFileType().value()));
          
        }else {
          
          parseGtfsRecords(csvParser, filterExcludedColumns(mapHeadersToGtfsKeys(headerMap)));
          
        }
    
        csvParser.close();
        gtfsInputReader.close();
        gtfsInputStream.close();
      }      
    }catch(Exception e) {
      LOGGER.severe(String.format("Error during parsing of GTFS file (%s - %s)",gtfsLocation.toString(), fileScheme.getFileType().value()));
    }
  }
  
  /** Register handler
   * 
   * @param handler to register
   */
  public void addHandler(final GtfsFileHandler<? extends GtfsObject> handler) {
    if(!handler.isCompatible(fileScheme)) {
      LOGGER.warning(String.format("DISCARD: GTFS handler incompatible with GTFS file reader for %s", fileScheme.toString()));
    }
    handlers.add(handler);
  }

  /** The file scheme of this reader indicating what file it is operating on
   * 
   * @return file scheme
   */
  public GtfsFileScheme getFileScheme() {
    return fileScheme;
  }
  
  /** The settings of this GTFS file reader
   * 
   * @return settings
   */
  public GtfsFileReaderSettings getSettings() {
    return settings;
  }
}
