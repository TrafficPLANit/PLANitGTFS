package org.goplanit.gtfs.converter.service;

import org.goplanit.converter.MultiConverterReader;
import org.goplanit.gtfs.converter.service.handler.GtfsPlanitFileHandlerRoutes;
import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.reader.GtfsFileReaderRoutes;
import org.goplanit.gtfs.reader.GtfsReaderFactory;
import org.goplanit.gtfs.scheme.GtfsFileSchemeFactory;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.*;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;

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
public class GtfsServicesReader implements MultiConverterReader<ServiceNetwork, RoutedServices> {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesReader.class.getCanonicalName());
  
  /** the settings for this reader */
  private final GtfsServicesReaderSettings settings;

  /** id token to use */
  private IdGroupingToken idToken;

  /**
   * Initialise the to be populated PLANit entities
   *
   * @return service network and route services to be populated
   */
  private Pair<ServiceNetwork, RoutedServices> initialiseBeforeParsing() {
    var serviceNetwork = new ServiceNetwork(idToken, settings.getReferenceNetwork());
    var routedServices = new RoutedServices(idToken, serviceNetwork);

    return Pair.of(serviceNetwork, routedServices);
  }

  /**
   * Log the settings and other information used
   */
  private void logInfo() {
    getSettings().log();
  }

  private void processStopTimes(GtfsServicesProfiler profiler) {
    /* 2) process to link routes to trips to stops (so we can assign supported modes to stops and do proper mapping
     * to transfer zones (see process Stops implementation for further comments)  */
  }

  private void processTrips(GtfsServicesProfiler profiler) {
    /* 2) process to link routes to trips  */
  }

  /**
   * Process GTFS routes. Capture modes of routes to use later on to identify supported mdoes for GTFS stops
   * which in turn are used to map to PLANit entities
   *
   * @param toBePopulated
   * @param profiler      to use
   */
  private void processRoutes(Pair<ServiceNetwork, RoutedServices> toBePopulated, GtfsServicesProfiler profiler) {
    LOGGER.info("Processing: parsing GTFS Routes...");

    /** handler that will process individual routes upon ingesting */
    var routesHandler = new GtfsPlanitFileHandlerRoutes(toBePopulated.first(),toBePopulated.second(), getSettings(), profiler.getGtfsRoutesProfiler());

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each route found */
    GtfsFileReaderRoutes routesFileReader = (GtfsFileReaderRoutes) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.ROUTES), getSettings().getInputDirectory());
    routesFileReader.addHandler(routesHandler);

    /** execute */
    routesFileReader.read();
  }

  /**
   * Execute the actual parsing
   *
   * @param handlerProfiler to use for tracking stats
   * @param toBePopulated service network and routes services to populate
   */
  protected void doMainProcessing(GtfsServicesProfiler handlerProfiler, Pair<ServiceNetwork, RoutedServices> toBePopulated) {

    LOGGER.info("Processing: Identifying GTFS services, populating PLANit memory model...");

    /* meta-data for routes including its mode */
    processRoutes(toBePopulated, handlerProfiler);
    /* meta-data for grouping of instances for a route via its service id */
    processTrips(handlerProfiler);
    /* matching routes and trips to stops at actual times */
    processStopTimes(handlerProfiler);

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
    var toBePopulated = initialiseBeforeParsing();

    GtfsServicesProfiler handlerProfiler = new GtfsServicesProfiler();
    logInfo();

    /* main processing  */
    doMainProcessing(handlerProfiler, toBePopulated);

    /* log stats */
    handlerProfiler.logProcessingStats();

    /* return parsed GTFS services in PLANit memory model form*/
    return toBePopulated;
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
