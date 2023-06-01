package org.goplanit.gtfs.reader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.goplanit.gtfs.enums.GtfsColumnType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.handler.GtfsFileHandler;
import org.goplanit.gtfs.entity.GtfsObject;
import org.goplanit.gtfs.entity.GtfsObjectFactory;
import org.goplanit.gtfs.scheme.GtfsFileScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;
import org.goplanit.gtfs.util.GtfsUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.StringUtils;

/**
 * A GTFS file reader containing generic code for any GTFS file
 * 
 * @author markr
 *
 */
public abstract class GtfsFileReaderBase {
  
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
    boolean unsupportedColumns = false;
    for(String headerEntry : headerMap.keySet()) {
      if(!GtfsKeyType.valueIn(supportedKeys,headerEntry.trim())) {
        LOGGER.warning(String.format("Encountered unknown GTFS column header (%s), column will be ignored",headerEntry));
        unsupportedColumns = true;
      }
    }

    return !unsupportedColumns;
  }  

  /** Map the headers in the file to the correct GTFS keys. Since the headers might have spaces or non-lowercase characters we preserve the 
   * actual parsed header as key but account for these anomalies when finding the appropriate key that goes with it. 
   * 
   * @param headerMap to create GtfsKey mapping for
   * @return created mapping
   */
  private Map<String, GtfsKeyType> mapHeadersToGtfsKeys(Map<String, Integer> headerMap) {
    Map<String, GtfsKeyType> headerToKeyMap = new HashMap<>();
    for(String headerEntry : headerMap.keySet()) {
      String comparableHeaderEntry = StringUtils.removeBOM(headerEntry.trim()).toLowerCase();
      GtfsKeyType.fromValue(comparableHeaderEntry).ifPresent( key -> headerToKeyMap.put(headerEntry, key));
    }
    return headerToKeyMap;
  }

  /** Create a copy of passed in columns but without any columns that are marked for exclusion.
   * 
   * @param gtfsFileColumns to filter
   * @return gtfsFileColumns without excluded columns
   */
  private Map<String, GtfsKeyType> filterExcludedColumns(final Map<String, GtfsKeyType> gtfsFileColumns) {
    Map<String, GtfsKeyType> filteredColumns = new HashMap<>(gtfsFileColumns);
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
   * @return numberOfParsedRecords
   */
  private long parseGtfsRecords(final CSVParser csvParser, final Map<String, GtfsKeyType> columnsToParse) {
    LongAdder numRecords = new LongAdder();
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

      numRecords.increment();
    }

    /* delegate to handler to finalise */
    for(GtfsFileHandler<? extends GtfsObject> handler : handlers) {
      handler.handleComplete();
    }

    return numRecords.longValue();
  }

  /** Explicitly indicate the expectations regarding the presence of this file. When marked as optional no warnings will be logged
   * when it is not present.
   *
   * @param filePresenceCondition to use
   */
  public void setPresenceCondition(GtfsFileConditions filePresenceCondition) {
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

  /** Constructor using default gtfs reader settings
   *
   * @param fileScheme the file scheme this file reader is based on
   * @param gtfsLocation to base file location to parse from on (dir or zip file)
   * @param filePresenceCondition to enforce
   */
  protected GtfsFileReaderBase(final GtfsFileScheme fileScheme, URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    this(fileScheme, gtfsLocation, new GtfsFileReaderSettings());
  }

  /**
   * Let concrete implementation determine the initially excluded columns (if any) based on the provided column type configuration passed in.
   * Note that we log a severe when the chosen column type is not matched, i.e., concrete classes should only call this implementation once
   * they have exhausted their specific column type configurations and not call this beforehand.
   *
   * @param columnType configuration to apply for initial column exclusions (if any)
   */
  protected void initialiseColumnConfiguration(GtfsColumnType columnType){
    // let concrete classes override this for types that can't be configured in this base class implementation
    switch (columnType){
      case NO_COLUMNS:
        getSettings().excludeColumns(GtfsUtils.getSupportedKeys(getFileScheme().getObjectType()).iterator());
        return;
      case ALL_COLUMNS:
        // nothing to exclude
        return;
      default:
        LOGGER.severe(String.format("Chosen GTFS column configuration (%s) not supported by base reader implementation",columnType));
    }
  }

  /** Constructor which enforces the file to be present
   * 
   * @param fileScheme the file scheme this file reader is based on
   * @param gtfsLocation to base file location to parse from on (dir or zip file)
   * @param settings to use
   */
  protected GtfsFileReaderBase(final GtfsFileScheme fileScheme, URL gtfsLocation, GtfsFileReaderSettings settings) {
    this(fileScheme, gtfsLocation, GtfsFileConditions.required(), settings);
  }

  /** Constructor
   *
   * @param fileScheme the file scheme this file reader is based on
   * @param gtfsLocation to base file location to parse from on (dir or zip file)
   * @param filePresenceCondition to apply (optional, required, conditionally required etc.)
   * @param settings to use
   */
  protected GtfsFileReaderBase(final GtfsFileScheme fileScheme, URL gtfsLocation, GtfsFileConditions filePresenceCondition, GtfsFileReaderSettings settings) {
    this.fileScheme = fileScheme;
    this.settings = settings;
    this.filePresenceCondition = filePresenceCondition;

    this.handlers = new HashSet<>();

    boolean validGtfsLocation = GtfsUtils.isValidGtfsLocation(gtfsLocation);
    this.gtfsLocation = validGtfsLocation ? gtfsLocation : null;
    if(!validGtfsLocation){
      LOGGER.warning(String.format("Provided GTFS location (%s)is neither a directory nor a zip file, unable to instantiate file reader", gtfsLocation));
    }
  }
  
  /**
   * Perform the reading of the file
   *
   * @param charSetToUse the charset to use
   */
  public void read(Charset charSetToUse) {
            
    try (InputStream gtfsInputStream =
             GtfsUtils.createInputStream(gtfsLocation, fileScheme, filePresenceCondition, settings.isLogGtfsFileInputStreamInfo())){
      if(gtfsInputStream!=null) {
        Reader gtfsInputReader = new InputStreamReader(gtfsInputStream, charSetToUse);
        CSVParser csvParser = new CSVParser(gtfsInputReader, CSVFormat.DEFAULT.withHeader());

        var headerWithBom = csvParser.getHeaderMap();
        Map<String, Integer> headerMap = new HashMap<>();
        headerWithBom.forEach( (k,v) -> headerMap.put(StringUtils.removeBOM(k),v));

        if(!isValid(headerMap)) {
          LOGGER.warning(String.format("Header for %s - %s contains ignored columns, ", gtfsLocation, fileScheme.getFileType().value()));
        }

        // use csv header map to preserve BOM as csv parser relies on exact mapping of header to obtain column entries
        long numRecords = parseGtfsRecords(csvParser, filterExcludedColumns(mapHeadersToGtfsKeys(headerWithBom)));
        if(settings.isLogGtfsFileInputStreamInfo()){
          LOGGER.info(String.format("Processed %d records from input stream", numRecords));
        }

        csvParser.close();
        gtfsInputReader.close();
        gtfsInputStream.close();
      }else{
        LOGGER.warning(String.format("Empty input stream for (location: %s, scheme: %s", gtfsLocation.toString(), fileScheme));
      }
    }catch(Exception e) {
      LOGGER.severe(String.format("Error during parsing of GTFS file (%s - %s)",gtfsLocation.toString(), fileScheme.getFileType().value()));
      throw new PlanItRunTimeException(e.getMessage(), e);
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

  /**
   * Reset this reader and its registered handlers
   */
  public void reset(){
    handlers.forEach( h -> h.reset());
  }
}
