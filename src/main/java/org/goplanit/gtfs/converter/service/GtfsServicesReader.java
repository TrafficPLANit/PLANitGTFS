package org.goplanit.gtfs.converter.service;

import org.goplanit.converter.PairConverterReader;
import org.goplanit.gtfs.converter.service.handler.GtfsServicesHandlerData;
import org.goplanit.gtfs.converter.service.handler.GtfsPlanitFileHandlerRoutes;
import org.goplanit.gtfs.converter.service.handler.GtfsPlanitFileHandlerStopTimes;
import org.goplanit.gtfs.converter.service.handler.GtfsPlanitFileHandlerTrips;
import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.reader.GtfsFileReaderRoutes;
import org.goplanit.gtfs.reader.GtfsFileReaderStopTimes;
import org.goplanit.gtfs.reader.GtfsFileReaderTrips;
import org.goplanit.gtfs.reader.GtfsReaderFactory;
import org.goplanit.gtfs.scheme.GtfsFileSchemeFactory;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.service.routed.RoutedServices;

import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Implementation of a GTFS services reader from GTFS files. This reads the following GTFS files:
 *  * <ul>
 *  *   <li>routes.txt</li>
 *  * </ul>
 *  The result is both the service network as well as the routed services as a PLANit memory model
 *
 * @author markr
 *
 */
public class GtfsServicesReader implements PairConverterReader<ServiceNetwork, RoutedServices> {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesReader.class.getCanonicalName());
  
  /** the settings for this reader */
  private final GtfsServicesReaderSettings settings;

  /** id token to use */
  private IdGroupingToken idToken;

  /**
   * Initialise the to be populated PLANit entities
   *
   * @return file handler data to track required data across the various GTFS PLANit file handlers
   */
  private GtfsServicesHandlerData initialiseBeforeParsing() {
    var serviceNetwork = new ServiceNetwork(idToken, settings.getReferenceNetwork());
    var routedServices = new RoutedServices(idToken, serviceNetwork);

    PlanItRunTimeException.throwIf(routedServices.getParentNetwork() != serviceNetwork, "Routed services its service network does not match the service network provided");
    PlanItRunTimeException.throwIf(!serviceNetwork.getTransportLayers().isEmpty() && serviceNetwork.getTransportLayers().isEachLayerEmpty(), "Service network is expected to have been initialise with empty layers before populating with GTFS routes");
    PlanItRunTimeException.throwIf(!routedServices.getLayers().isEmpty() && routedServices.getLayers().isEachLayerEmpty(), "Routed services layers are expected to have been initialised empty when populating with GTFS routes");

    /* create a new service network layer for each physical layer that is present */
    settings.getReferenceNetwork().getTransportLayers().forEach(parentLayer -> serviceNetwork.getTransportLayers().getFactory().registerNew(parentLayer));

    /* create a routed services for each service layer that we created */
    serviceNetwork.getTransportLayers().forEach(parentLayer -> routedServices.getLayers().getFactory().registerNew(parentLayer));

    /* profiler to use */
    GtfsServicesHandlerProfiler handlerProfiler = new GtfsServicesHandlerProfiler();

    /** provide access to the service network and routed services via the file handler data tracking used throughout the parsing process */
    return new GtfsServicesHandlerData(getSettings(), serviceNetwork, routedServices, handlerProfiler);
  }

  /**
   * Log the settings and other information used
   */
  private void logSettings() {
    getSettings().log();
  }

  /**
   * Process GTFS frequencies of routes/trips fr usage in PLANit
   *
   * @param fileHandlerData to use
   */
  private void processFrequencies(GtfsServicesHandlerData fileHandlerData) {
    //todo: not yet implemented
  }

  /**
   * Process GTFS stop times of routes/trips fr usage in PLANit
   *
   * @param fileHandlerData to use
   */
  private void processStopTimes(GtfsServicesHandlerData fileHandlerData) {
    LOGGER.info("Processing: parsing GTFS trip stop times...");

    /** handler that will process individual trip stop times upon ingesting */
    var tripStopTimeHandler = new GtfsPlanitFileHandlerStopTimes(fileHandlerData);

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each trip stop time found */
    GtfsFileReaderStopTimes stopTimeFileReader = (GtfsFileReaderStopTimes) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.STOP_TIMES), getSettings().getInputDirectory());
    stopTimeFileReader.addHandler(tripStopTimeHandler);

    /** execute */
    stopTimeFileReader.read();
  }

  /**
   * Process GTFS trips for usage in PLANit
   *
   * @param fileHandlerData to use
   */
  private void processTrips(GtfsServicesHandlerData fileHandlerData) {
    LOGGER.info("Processing: parsing GTFS trips...");

    /** handler that will process individual trips upon ingesting */
    var tripsHandler = new GtfsPlanitFileHandlerTrips(fileHandlerData);

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each route found */
    GtfsFileReaderTrips tripsFileReader = (GtfsFileReaderTrips) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.TRIPS), getSettings().getInputDirectory());
    tripsFileReader.addHandler(tripsHandler);

    /** execute */
    tripsFileReader.read();
  }

  /**
   * Process GTFS routes. Capture modes of routes to use later on to identify supported mdoes for GTFS stops
   * which in turn are used to map to PLANit entities
   *
   * @param fileHandlerData containing all data to track and resources needed to perform the processing
   */
  private void processRoutes(GtfsServicesHandlerData fileHandlerData) {
    LOGGER.info("Processing: parsing GTFS Routes...");

    /** handler that will process individual routes upon ingesting */
    var routesHandler = new GtfsPlanitFileHandlerRoutes(fileHandlerData);

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each route found */
    GtfsFileReaderRoutes routesFileReader = (GtfsFileReaderRoutes) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.ROUTES), getSettings().getInputDirectory());
    routesFileReader.addHandler(routesHandler);

    /** execute */
    routesFileReader.read();
  }

  /**
   * Log some stats on the now available PLANit entities in memory
   */
  private void logPlanitStats(GtfsServicesHandlerData fileHandlerData) {

    fileHandlerData.getServiceNetwork().logInfo(LoggingUtils.serviceNetworkPrefix(fileHandlerData.getServiceNetwork().getId()));
    fileHandlerData.getRoutedServices().logInfo(LoggingUtils.routedServicesPrefix(fileHandlerData.getRoutedServices().getId()));

  }

  /**
   * Execute the actual parsing
   *
   * @param fileHandlerData containing all data to track and resources needed to perform the processing
   */
  protected void doMainProcessing(GtfsServicesHandlerData fileHandlerData) {

    LOGGER.info("Processing: Identifying GTFS services, populating PLANit memory model...");

    /* meta-data for routes including its mode */
    processRoutes(fileHandlerData);
    /* meta-data for grouping of instances for a route via its service id */
    processTrips(fileHandlerData);
    /* matching routes and trips to stops at actual times */
    processStopTimes(fileHandlerData);
    /* matching routes and trips to stops based on frequency information */
    processFrequencies(fileHandlerData);

    /* optional optimisation/processing */
    //TODO: option to consolidate PLANit trips by having multiple departure times per trip (instead of one)
    // if schedule for each departure is the same. currently single departure + schedule per trip

    //TODO: option to convert schedules to frequency based approach

    //todo clean up indices that we no longer need to save memory for gtfs transfer zone converion next

    LOGGER.info("Processing: GTFS services Done");
  }

  /** Constructor where settings are directly provided such that input information can be extracted from it
   *
   * @param settings to use
   */
  protected GtfsServicesReader(final GtfsServicesReaderSettings settings) {
    this(IdGroupingToken.collectGlobalToken(), settings);
  }

  /** Constructor where settings are directly provided such that input information can be extracted from it
   * 
   * @param idToken to use for the routed services and service network ids
   * @param settings to use
   */
  protected GtfsServicesReader(final IdGroupingToken idToken, final GtfsServicesReaderSettings settings) {
    this.settings = settings;
    this.idToken = idToken;
  }  

  /**
   * {@inheritDoc}
   */
  @Override
  public Pair<ServiceNetwork, RoutedServices> read(){

    PlanItRunTimeException.throwIf(StringUtils.isNullOrBlank(getSettings().getCountryName()), "Country not set for GTFS services reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(getSettings().getInputDirectory(), "Input directory not set for GTFS services reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(getSettings().getReferenceNetwork(),"Reference network not available when parsing GTFS services, unable to proceed");

    /* prepare for parsing */
    var fileHandlerData = initialiseBeforeParsing();

    logSettings();

    /* main processing  */
    doMainProcessing(fileHandlerData);

    /* log stats */
    fileHandlerData.getProfiler().logProcessingStats();
    logPlanitStats(fileHandlerData);

    /* return parsed GTFS services in PLANit memory model form*/
    return Pair.of(fileHandlerData.getServiceNetwork(), fileHandlerData.getRoutedServices());
  }

  /**
   * GTFS Services are ingested and lead to PLANit service nodes to be created based on GTFS stop ids. When at some later point in time
   * these PLANit service nodes are to be linked to PLANit transfer zones (which in turn have an association with a GTFS stop) the mapping
   * between PLANit service node and its underlying GTFS stop needs to remain available. This function provides this mapping.
   * <p>
   *   For now this mapping is purely based on the external id, but if this changes using this explicit functional approach allows
   *   us to change this without having to change the process flow itself
   * </p>
   *
   * @return mapping from PLANit service node to underlying source GTFS stop id
   */
  public Function<ServiceNode, String> getServiceNodeToGtfsStopIdMapping(){
    return GtfsServicesHandlerData.getServiceNodeToGtfsStopIdMapping();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GtfsServicesReaderSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getTypeDescription() {
    return "GTFS services Reader";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    settings.reset();
  }

}
