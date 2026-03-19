package org.goplanit.gtfs.reader;

import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.goplanit.gtfs.entity.GtfsObject;
import org.goplanit.gtfs.entity.GtfsObjectFactory;
import org.goplanit.gtfs.enums.GtfsColumnType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.handler.GtfsFileHandler;
import org.goplanit.gtfs.scheme.GtfsFileScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;
import org.goplanit.gtfs.util.GtfsUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.StringUtils;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

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
  private long parseGtfsRecords(final CsvParser csvParser,
                                final Map<String, GtfsKeyType> columnsToParse,
                                final Map<String, Integer> columnToIndexMap) {

    LongAdder numRecords = new LongAdder();

    String[] row;
    while ((row = csvParser.parseNext()) != null) {
      // 1. Create GTFS object for this row
      GtfsObject gtfsObject = GtfsObjectFactory.create(fileScheme.getObjectType());

      // 2. Populate fields
      for (Map.Entry<String, GtfsKeyType> entry : columnsToParse.entrySet()) {
        final String columnName = entry.getKey();
        final GtfsKeyType key = entry.getValue();

        // get index in raw row
        final Integer idx = columnToIndexMap.get(columnName);
        if (idx == null) {
          continue; // ignore columns not present
        }

        final String value = row[idx];
        gtfsObject.put(key, value);
      }

      // 3. Delegate to each handler (same as before)
      for (GtfsFileHandler<? extends GtfsObject> handler : handlers) {
        handler.handleRaw(gtfsObject);
      }

      numRecords.increment();
    }

    // Finalise handlers
    for (GtfsFileHandler<? extends GtfsObject> handler : handlers) {
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
    this(fileScheme, gtfsLocation, filePresenceCondition, new GtfsFileReaderSettings());
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

    // use Univocity as it is faster than Commons CSV parser
    CsvParserSettings csvParserSettings = new CsvParserSettings();
    csvParserSettings.setHeaderExtractionEnabled(false); // does not work intuitively, do it manually instead
    csvParserSettings.setLineSeparatorDetectionEnabled(true);

    // Dialect tuned to GTFS expectations:
    CsvFormat format = csvParserSettings.getFormat();
    format.setDelimiter(',');
    format.setQuote('"');
    format.setQuoteEscape('"');

    // If GTFS is clean, avoid extra whitespace work:
//    settings.setIgnoreLeadingWhitespaces(false);
//    settings.setIgnoreTrailingWhitespaces(false);
//    settings.setNullValue("");
//    settings.setEmptyValue("");



    // Create the parser
    CsvParser parser = new CsvParser(csvParserSettings);

    try (InputStream is = GtfsUtils.createInputStream(
            gtfsLocation,
            fileScheme,
            filePresenceCondition,
            settings.isLogGtfsFileInputStreamInfo())) { // from zip entry
      if(is.available() == 0){
        if(filePresenceCondition.isOptional()){
          LOGGER.info(String.format("Skipping optional %s: not available",this.fileScheme.getFileType().value()));
          return;
        }else{
          throw new PlanItRunTimeException("non-optional %s not available, this should not happen",
                  this.fileScheme.getFileType().value());
        }
      }
      parser.beginParsing(is, charSetToUse);

      // parse first row as header
      String[] headers = parser.parseNext();
      if(headers == null){
        LOGGER.severe(String.format("Header for %s - %s seems to be missing, check if file is available or valid",
            gtfsLocation, fileScheme.getFileType().value()));
      }else{

        Map<String, Integer> headerMap = new HashMap<>();
        for (int index =0 ; index< headers.length; index ++) {
          headerMap.put(StringUtils.removeBOM(headers[index]), index);
        }

        if(!isValid(headerMap)) {
          LOGGER.warning(String.format("Header for %s - %s contains ignored columns, ",
              gtfsLocation, fileScheme.getFileType().value()));
        }

        // use csv header map to preserve BOM as csv parser relies on exact mapping of header to obtain column entries
        long numRecords = parseGtfsRecords(parser, filterExcludedColumns(mapHeadersToGtfsKeys(headerMap)), headerMap);
        if(settings.isLogGtfsFileInputStreamInfo()){
          LOGGER.info(String.format("Processed %d records from input stream", numRecords));
        }

      }

    }catch(Exception e){
      LOGGER.warning(String.format("Input stream not working (location: %s, scheme: %s",
              gtfsLocation.toString(), fileScheme));
      LOGGER.severe(String.format("Error during parsing of GTFS file (%s - %s)",
              gtfsLocation.toString(), fileScheme.getFileType().value()));
      throw new PlanItRunTimeException(e.getMessage(), e);
    } finally {
      parser.stopParsing();
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
