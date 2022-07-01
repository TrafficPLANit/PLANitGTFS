package org.goplanit.gtfs.converter.zoning;

import org.goplanit.converter.zoning.ZoningReader;
import org.goplanit.gtfs.converter.zoning.handler.GtfsPlanitFileHandlerStops;
import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerProfiler;
import org.goplanit.gtfs.entity.GtfsObject;
import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.handler.GtfsFileHandler;
import org.goplanit.gtfs.handler.GtfsFileHandlerTrips;
import org.goplanit.gtfs.reader.GtfsFileReaderStops;
import org.goplanit.gtfs.reader.GtfsFileReaderTrips;
import org.goplanit.gtfs.reader.GtfsReaderFactory;
import org.goplanit.gtfs.scheme.GtfsFileSchemeFactory;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.resource.ResourceUtils;
import org.goplanit.zoning.Zoning;

import java.net.URL;
import java.util.logging.Logger;

/**
 * Parse GTFS input in supplement or populate a PLANit zoning instance with the parsed/matched GTFS entities.
 * In case an existing zoning instance is provided, the GTFS will supplement the existing public transport infrastructure
 * where possible, while if the zoning is empty it will create new entries for each of the found transfer zones (pt stops etc).
 *
 * @author markr
 *
 */
public class GtfsZoningReader implements ZoningReader {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningReader.class.getCanonicalName());

  /** the settings the user can configure for parsing transfer zones */
  private final GtfsPublicTransportReaderSettings gtfsSettings;

  /** zoning to populate */
  private Zoning zoning;

  /**
   * Log some information about this reader's configuration
   */
  private void logInfo() {
    LOGGER.info(String.format("GTFS input file: %s",getSettings().getInputSource()));
  }

  /**
   * perform final preparation before conducting parsing of OSM pt entities
   */
  private void initialiseBeforeParsing() {

    //TODO: if data needs to be tracked do it via a data class someting like the below
    //this.zoningReaderData = new GtfsZoningReaderData(getSettings().getCountryName());

    //todo: spatially index all existing transfer zones
    //zoningReaderData.getPlanitData().initialiseSpatiallyIndexedLinks(getSettings().getReferenceNetwork());

    //todo: support bounding polygon, which in combined parser with OSM) will be based on network bounding box. However within reader this is not
    //      known as it can also be used in a stand-alone fashion...so validate
    /* make sure that if a bounding box has been set, the zoning bounding box does not exceed the network bounding box
     * since it makes little sense to try and parse pt infrastructure outside of the network's geographically parsed area */
    //validateZoningBoundingPolygon();

  }

  /**
   * Process the GTFS stops
   *
   * @param profiler to use
   */
  private void processStops(GtfsZoningHandlerProfiler profiler) {
    /* PLANit specific handler */
    var stopsHandler = new GtfsPlanitFileHandlerStops(this.zoning, getSettings(), profiler);

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each stop found */
    GtfsFileReaderTrips tripsFileReader = (GtfsFileReaderTrips) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.STOPS), ResourceUtils.getResourceUrl(getSettings().getInputSource()));
    tripsFileReader.addHandler(stopsHandler);

    /* configuration of reader */
    //TODO: choose what to include/exclude
    //tripsFileReader.getSettings().excludeColumns(GtfsKeyType.TRIP_HEADSIGN);

    /* execute */
    tripsFileReader.read();
  }


    /**
     * Conduct main processing step of zoning reader given the information available from pre-processing
     *
     * @param profiler to use
     */
  private void doMainProcessing(GtfsZoningHandlerProfiler profiler) {
    LOGGER.info("Processing: Identifying GTFS entities, supplementing PLANit zoning memory model...");

    processStops(profiler);

    LOGGER.info("Processing: Done");
  }

  /**
   * Constructor. Requires user to set reference network and networkToZoning data manually afterwards
   *
   * @param settings to use
   * @param zoningToPopulate zoning to populate
   */
  protected GtfsZoningReader(GtfsPublicTransportReaderSettings settings, Zoning zoningToPopulate){
    this.gtfsSettings = settings;
    this.zoning = zoningToPopulate;
  }

  /**
   * Constructor. Requires user to set networkToZoning data manually afterwards
   *
   * @param inputSource to parse from
   * @param countryName this zoning is used for
   * @param zoningToPopulate zoning to populate
   * @param referenceNetwork to use
   */
  protected GtfsZoningReader(String inputSource, String countryName, Zoning zoningToPopulate, MacroscopicNetwork referenceNetwork){
    this.gtfsSettings = new GtfsPublicTransportReaderSettings(inputSource, countryName, referenceNetwork);
    this.zoning = zoningToPopulate;
  }

  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a PLANit Zoning instance given the configuration options that have been set
   * 
   * @return macroscopic zoning that has been parsed
   */
  @Override
  public Zoning read(){
    PlanItRunTimeException.throwIf(StringUtils.isNullOrBlank(getSettings().getCountryName()), "Country not set for GTFS zoning reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(getSettings().getInputSource(), "Input source not set for GTFS zoning reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(getSettings().getReferenceNetwork(),"Reference network not available when parsing GTFS zoning, unable to proceed");

    /* prepare for parsing */
    initialiseBeforeParsing();
    
    GtfsZoningHandlerProfiler handlerProfiler = new GtfsZoningHandlerProfiler();
    logInfo();

    /* main processing  */
    doMainProcessing(handlerProfiler);

    /* log stats */
    handlerProfiler.logProcessingStats(zoning);

    /* return parsed/augmented zoning */
    return this.zoning;    
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    /* free memory */
    getSettings().reset();
  }  

  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the settings
   */
  public GtfsPublicTransportReaderSettings getSettings() {
    return gtfsSettings;
  }



}
