package org.goplanit.gtfs.converter.zoning;

import org.goplanit.converter.zoning.ZoningReader;
import org.goplanit.gtfs.converter.zoning.handler.GtfsPlanitFileHandlerStops;
import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerProfiler;
import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.reader.GtfsFileReaderStops;
import org.goplanit.gtfs.reader.GtfsReaderFactory;
import org.goplanit.gtfs.scheme.GtfsFileSchemeFactory;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.zoning.Zoning;

import java.util.function.Function;
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
  private final GtfsZoningReaderSettings gtfsSettings;

  /** zoning to populate */
  private final Zoning zoning;

  /** Service network to use to improve matching of GTFS stops (optional)*/
  private final ServiceNetwork serviceNetwork;

  /** routed services to use to improve matching of GTFS stops (optional) */
  private final RoutedServices routedServices;

  /** function that allows user to map a GTFS stop id to the underlying transfer zone (after {@link #read()} has been invoked) */
  private Function<String, TransferZone> gtfsStopIdToTransferZoneMapping;

  /** flag whether {@link #read()} has been invoked, false after {@link #reset()}  */
  private boolean readInvoked;

  /**
   * Log some information about this reader's configuration
   */
  private void logSettings() {
    getSettings().log();
  }

  /**
   * perform final preparation before conducting parsing of OSM pt entities
   */
  private GtfsZoningHandlerData initialiseBeforeParsing() {
    readInvoked = false;
    return new GtfsZoningHandlerData(getSettings(),zoning, serviceNetwork, routedServices, new GtfsZoningHandlerProfiler());
  }

  /**
   * Process the GTFS stops
   *
   * @param gtfsZoningHandlerData to use
   */
  private void processStops(GtfsZoningHandlerData gtfsZoningHandlerData) {
    LOGGER.info("Processing: mapping GTFS Stops...");
    /* PLANit specific handler */
    var stopsHandler = new GtfsPlanitFileHandlerStops(gtfsZoningHandlerData);

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each stop found */
    GtfsFileReaderStops stopsFileReader = (GtfsFileReaderStops) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.STOPS), getSettings().getInputDirectory());
    stopsFileReader.addHandler(stopsHandler);

    /* configuration of reader */
    stopsFileReader.getSettings().excludeColumns(GtfsKeyType.STOP_CODE);
    stopsFileReader.getSettings().excludeColumns(GtfsKeyType.STOP_URL);
    stopsFileReader.getSettings().excludeColumns(GtfsKeyType.STOP_TIMEZONE);
    stopsFileReader.getSettings().excludeColumns(GtfsKeyType.WHEELCHAIR_BOARDING);

    /* execute */
    stopsFileReader.read();
  }


    /**
     * Conduct main processing step of zoning reader given the information available from pre-processing
     *
     * @param zoningHandlerData to use
     */
  private void doMainProcessing(GtfsZoningHandlerData zoningHandlerData) {
    LOGGER.info("Processing: Identifying GTFS Stops, supplementing PLANit transfer zones memory model...");
    processStops(zoningHandlerData);
    LOGGER.info("Processing: GTFS stops Done");

    //TODO: update all XML ids to internal ids because of mixing of OSM and GTFS stop ids, they are no longer guaranteed to
    //      be unique
  }

  /**
   * Constructor. Requires user to set reference network and networkToZoning data manually afterwards
   *
   * @param settings to use
   * @param zoningToPopulate zoning to populate
   */
  protected GtfsZoningReader(GtfsZoningReaderSettings settings, Zoning zoningToPopulate){
    this(settings,zoningToPopulate, null, null);
  }

  /**
   * Constructor.
   *
   * @param settings to use
   * @param zoningToPopulate zoning to populate
   * @param serviceNetwork the compatible PLANit service network that is assumed to have been constructed from the same GTFS source files as this zoning reader will use
   * @param routedServices the compatible PLANit routed services that is assumed to have been constructed from the same GTFS source files as this zoning reader will use
   */
  protected GtfsZoningReader(GtfsZoningReaderSettings settings, Zoning zoningToPopulate, ServiceNetwork serviceNetwork, RoutedServices routedServices){
    this.gtfsSettings = settings;
    this.zoning = zoningToPopulate;
    this.serviceNetwork = serviceNetwork;
    this.routedServices = routedServices;
  }

  /**
   * Parse a GTFS files and convert it into a PLANit Zoning instance, or supplement the transfer zones of an already existing instance
   * given the configuration options that have been set
   * 
   * @return macroscopic zoning that has been parsed or supplemented
   */
  @Override
  public Zoning read(){
    PlanItRunTimeException.throwIf(StringUtils.isNullOrBlank(getSettings().getCountryName()), "Country not set for GTFS zoning reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(getSettings().getInputDirectory(), "Input source not set for GTFS zoning reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(getSettings().getReferenceNetwork(),"Reference network not available when parsing GTFS zoning, unable to proceed");

    /* prepare for parsing */
    var zoningHandlerData = initialiseBeforeParsing();

    logSettings();

    /* main processing  */
    doMainProcessing(zoningHandlerData);

    /* log stats */
    zoningHandlerData.getProfiler().logProcessingStats(zoning);

    /* generate mapping function now that mapping is known, for third parties to use if needed */
    gtfsStopIdToTransferZoneMapping = zoningHandlerData.createGtfsStopToTransferZoneMappingFunction();
    readInvoked = true;

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
    /* reset state */
    readInvoked = false;
    gtfsStopIdToTransferZoneMapping = null;
  }  

  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the settings
   */
  public GtfsZoningReaderSettings getSettings() {
    return gtfsSettings;
  }

  /**
   * Provide mapping between GTFS Stop id and its found/created PLANit transfer zone (if any)
   *
   * @return function that takes a GTFS stop id (String) and produces the PLANit transfer zone that goes with it if any)
   */
  public Function<String, TransferZone> getGtfsStopIdToTransferZoneMapping() {
    if(!readInvoked){
      LOGGER.warning("Unable to provide GTFS Stop id to transfer zone mapping before read() has been invoked on reader, ignored");
      return null;
    }
    return gtfsStopIdToTransferZoneMapping;
  }


}
