package org.planit.gtfs.reader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.planit.gtfs.GtfsUtils;
import org.planit.gtfs.handler.GtfsFileHandler;
import org.planit.gtfs.model.GtfsObject;
import org.planit.gtfs.scheme.GtfsFileScheme;
import org.planit.utils.misc.UrlUtils;

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

  /** Parse entries for given parser
   * 
   * @param csvParser to use
   */
  private void parseGtfsRecords(final CSVParser csvParser) {
    Iterator<CSVRecord> entryIterator = csvParser.iterator();
    while(entryIterator.hasNext()) {
      CSVRecord gtfsEntryRecord = entryIterator.next();
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
    if(validGtfsLocation){
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
        
        parseGtfsRecords(csvParser);
    
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
