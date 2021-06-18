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
import org.planit.gtfs.GtfsUtils;
import org.planit.gtfs.enums.GtfsKeyType;
import org.planit.gtfs.handler.GtfsFileHandler;
import org.planit.gtfs.model.GtfsObject;
import org.planit.gtfs.model.GtfsObjectFactory;
import org.planit.gtfs.scheme.GtfsFileScheme;

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

  /** Constructor
   * 
   * @param fileScheme the file scheme this file reader is based on
   * @param gtfsLocation to base file location to parse from on (dir or zip file)
   */
  public GtfsFileReaderBase(final GtfsFileScheme fileScheme, URL gtfsLocation) {
    this.fileScheme = fileScheme;
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
    
    try ( InputStream gtfsInputStream = GtfsUtils.createInputStream(gtfsLocation, fileScheme);
          Reader gtfsInputReader = new InputStreamReader(gtfsInputStream);){

        CSVParser csvParser = new CSVParser(gtfsInputReader, CSVFormat.DEFAULT.withHeader());
        Map<String, Integer> headerMap = csvParser.getHeaderMap();
        if(!isValid(headerMap)) {
          
          LOGGER.warning(String.format("Invalid header for %s - %s, ignore file", gtfsLocation.toString(), fileScheme.getFileType().value()));
          
        }else {
          
          parseGtfsRecords(csvParser, mapHeadersToGtfsKeys(headerMap));
          
        }
    
        csvParser.close();
        gtfsInputReader.close();
        gtfsInputStream.close();
    
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

  public GtfsFileScheme getFileScheme() {
    return fileScheme;
  }
}
