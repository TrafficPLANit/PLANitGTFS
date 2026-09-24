package org.goplanit.gtfs.converter.service;

import org.goplanit.converter.PairConverterReader;
import org.goplanit.gtfs.converter.diagnostics.GtfsCoverageReport;
import org.goplanit.gtfs.converter.diagnostics.GtfsEntityScope;
import org.goplanit.gtfs.converter.diagnostics.GtfsScopeDimension;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseIssue;
import org.goplanit.gtfs.converter.service.handler.*;
import org.goplanit.gtfs.entity.GtfsCalendar;
import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.reader.*;
import org.goplanit.gtfs.reader.GtfsFileReaderStops;
import org.goplanit.gtfs.converter.service.handler.GtfsPlanitFileHandlerStopScope;
import org.goplanit.gtfs.scheme.GtfsFileSchemeFactory;
import org.goplanit.gtfs.util.GtfsConverterReaderHelper;
import org.goplanit.gtfs.util.GtfsRoutedServicesModifierUtils;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.service.routed.RoutedServices;

import java.nio.charset.StandardCharsets;

import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementation of a GTFS services reader from GTFS files. This reads the following GTFS files:
 *  <ul>
 *    <li>routes.txt</li>
 *  </ul>
 *  The result is both the service network as well as the routed services as a PLANit memory model
 *
 * @author markr
 *
 */
public class GtfsServicesReader implements PairConverterReader<ServiceNetwork, RoutedServices> {

  /**
   * Collect the GTFS ids of the routes that still hold a routed service
   *
   * @param routedServices to collect from
   * @return GTFS route ids present
   */
  private static Set<String> collectGtfsRouteIds(final RoutedServices routedServices) {
    Set<String> gtfsRouteIds = new HashSet<>();
    routedServices.getLayers().forEach(layer -> layer.getSupportedModes().forEach(
        mode -> layer.getServicesByMode(mode).forEach(
            routedService -> gtfsRouteIds.add(String.valueOf(routedService.getExternalId())))));
    return gtfsRouteIds;
  }

  /**
   * Collect the GTFS ids of the trips that still hold a schedule based routed trip
   *
   * @param routedServices to collect from
   * @return GTFS trip ids present
   */
  private static Set<String> collectGtfsTripIds(final RoutedServices routedServices) {
    Set<String> gtfsTripIds = new HashSet<>();
    routedServices.getLayers().forEach(layer -> layer.getSupportedModes().forEach(
        mode -> layer.getServicesByMode(mode).forEach(
            routedService -> routedService.getTripInfo().getScheduleBasedTrips().forEach(
                tripSchedule -> gtfsTripIds.add(String.valueOf(tripSchedule.getExternalId()))))));
    return gtfsTripIds;
  }



  /**
   * Record the GTFS trips whose only stop within the chosen filters left them without a single leg, which the clean-up
   * just removed. Registered here rather than in the clean-up itself, that being where PLANit entities are pruned
   * without knowing what they were read from
   *
   * @param fileHandlerData to register with
   * @param gtfsTripIdsBeforeRemoval present before the clean-up ran
   */
  private static void registerRemovedGtfsTrips(
      final GtfsServicesHandlerData fileHandlerData, final Set<String> gtfsTripIdsBeforeRemoval) {
    var remaining = collectGtfsTripIds(fileHandlerData.getRoutedServices());
    var diagnostics = fileHandlerData.getDiagnostics();
    /* named for why the trip was removed, which is that it was left without a leg. Where it stood when that happened
     * is recorded against the occurrence, so the issue need not restate it and must not claim a spatial filter that
     * may not even be running */
    gtfsTripIdsBeforeRemoval.stream().filter(gtfsTripId -> !remaining.contains(gtfsTripId)).forEach(
        gtfsTripId -> diagnostics.registerIssue(GtfsParseIssue.TRIP_WITHOUT_LEGS, gtfsTripId));
  }

  /**
   * Record the GTFS routes left without any trip surviving the chosen filters, which the clean-up just removed. The
   * diagnostics recover the route type the route was seen under, so that what is seen and what is discarded split the
   * same way
   *
   * @param fileHandlerData to register with
   * @param gtfsRouteIdsBeforeRemoval present before the clean-up ran
   */
  private static void registerRemovedGtfsRoutes(
      final GtfsServicesHandlerData fileHandlerData, final Set<String> gtfsRouteIdsBeforeRemoval) {
    var remaining = collectGtfsRouteIds(fileHandlerData.getRoutedServices());
    var diagnostics = fileHandlerData.getDiagnostics();
    /* named for why the route was removed, which is that nothing was left to run on it */
    gtfsRouteIdsBeforeRemoval.stream().filter(gtfsRouteId -> !remaining.contains(gtfsRouteId)).forEach(
        gtfsRouteId -> diagnostics.registerIssue(GtfsParseIssue.ROUTE_WITHOUT_TRIPS, gtfsRouteId));
  }

  /**
   * Record the GTFS entities the area the physical network covers places outside it, which is settled once every stop
   * time naming them has been read.
   * <p>
   * That area is reported against rather than parsed by, so nothing removes what it places outside and the respect
   * would otherwise state how much it ruled out without anything saying why. Only what survived the clean-ups is swept,
   * an entity they removed already carrying the reason it went
   * </p>
   *
   * @param fileHandlerData to register with
   */
  private static void registerGtfsEntitiesOutsideNetworkArea(final GtfsServicesHandlerData fileHandlerData) {
    var diagnostics = fileHandlerData.getDiagnostics();
    collectGtfsRouteIds(fileHandlerData.getRoutedServices()).stream().filter(
        gtfsRouteId -> diagnostics.getSettledScope(
            GtfsObjectType.ROUTE, GtfsScopeDimension.SPATIAL, gtfsRouteId) == GtfsEntityScope.OUT).forEach(
        gtfsRouteId -> diagnostics.registerIssue(GtfsParseIssue.ROUTE_OUTSIDE_NETWORK_AREA, gtfsRouteId));
    collectGtfsTripIds(fileHandlerData.getRoutedServices()).stream().filter(
        gtfsTripId -> diagnostics.getSettledScope(
            GtfsObjectType.TRIP, GtfsScopeDimension.SPATIAL, gtfsTripId) == GtfsEntityScope.OUT).forEach(
        gtfsTripId -> diagnostics.registerIssue(GtfsParseIssue.TRIP_OUTSIDE_NETWORK_AREA, gtfsTripId));
  }

  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesReader.class.getCanonicalName());

  /** the reference network to use during parsing of the pt zones */
  private MacroscopicNetwork referenceNetwork = null;
  
  /** the settings for this reader */
  private final GtfsServicesReaderSettings settings;

  /** id token to use */
  private IdGroupingToken idToken;

  /** what became of the GTFS entities read, available once reading completed */
  private GtfsParseDiagnostics rawGtfsEntityDiagnostics;

  /** whether this reader reports what became of the feed itself, which it does unless it is one stage of a wider parse */
  private boolean reportCoverage = true;

  /**
   * Initialise the to be populated PLANit entities
   *
   * @return file handler data to track required data across the various GTFS PLANit file handlers
   */
  private GtfsServicesHandlerData initialiseBeforeParsing() {
    var serviceNetwork = new ServiceNetwork(idToken, this.referenceNetwork);
    var routedServices = new RoutedServices(idToken, serviceNetwork);

    PlanItRunTimeException.throwIf(routedServices.getParentNetwork() != serviceNetwork,
        "Routed services its service network does not match the service network provided");
    PlanItRunTimeException.throwIf(!serviceNetwork.getTransportLayers().isEmpty()
        && serviceNetwork.getTransportLayers().isEachLayerEmpty(),
        "Service network is expected to have been initialise with empty layers before populating " +
            "with GTFS routes");
    PlanItRunTimeException.throwIf(!routedServices.getLayers().isEmpty() &&
        routedServices.getLayers().isEachLayerEmpty(),
        "Routed services layers are expected to have been initialised empty when populating with GTFS routes");

    /* sync the PLANit modes to the configured modes in the settings if needed */
    GtfsConverterReaderHelper.syncActivatedPlanitPredefinedModesBeforeParsing(
        getSettings(), serviceNetwork.getParentNetwork());

    /* create a new service network layer for each physical layer that is present */
    this.referenceNetwork.getTransportLayers().forEach(
        parentLayer -> serviceNetwork.getTransportLayers().getFactory().registerNew(parentLayer));

    /* create a routed services for each service layer that we created */
    serviceNetwork.getTransportLayers().forEach(
        parentLayer -> routedServices.getLayers().getFactory().registerNew(parentLayer));

    /* profiler to use, recording into diagnostics sized as configured before any entity reaches them */
    GtfsServicesHandlerProfiler handlerProfiler = new GtfsServicesHandlerProfiler(
        GtfsParseDiagnostics.create(
            getSettings().getDiagnosticsRetentionLimit(), getSettings().getDiagnosticsSampleSize()));

    /* provide access to the service network and routed services via the file handler data tracking used throughout
     * the parsing process */
    return new GtfsServicesHandlerData(getSettings(), serviceNetwork, routedServices, handlerProfiler);
  }

  /**
   * Log the settings and other information used
   */
  private void logSettings() {
    getSettings().logSettings();
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
   * Process GTFS stop times of routes/trips for usage in PLANit
   *
   * @param fileHandlerData to use
   */
  private void processStopTimes(GtfsServicesHandlerData fileHandlerData) {
    LOGGER.info("Processing: parsing GTFS trip stop times...");

    /* handler that will process individual trip stop times upon ingesting */
    var tripStopTimeHandler = new GtfsPlanitFileHandlerStopTimes(
        fileHandlerData, settings.getActivatedLoggingForGtfsRoutesByShortName());

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each trip stop time found */
    GtfsFileReaderStopTimes stopTimeFileReader = (GtfsFileReaderStopTimes) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.STOP_TIMES), getSettings().getInputSource());
    stopTimeFileReader.addHandler(tripStopTimeHandler);

    /* execute */
    stopTimeFileReader.read(StandardCharsets.UTF_8);

    /* no stop time follows the last trip to mark its scope as settled, so it is considered here */
    tripStopTimeHandler.settleFinalTrip();

    /* logging in case user required bespoke tracking of GTFS stop frequented GTFS routes */
    tripStopTimeHandler.getUniqueRoutesForTrackedGtfsStops().forEach(
        (key, value) -> LOGGER.info(String.format("GTFS stop %s is visited by GTFS routes [%s]",
        key, String.join(",", value))));
  }

  /**
   * Process GTFS trips for usage in PLANit
   *
   * @param fileHandlerData to use
   */
  private void processTrips(GtfsServicesHandlerData fileHandlerData) {
    LOGGER.info("Processing: parsing GTFS trips...");

    /* handler that will process individual trips upon ingesting */
    var tripsHandler = new GtfsPlanitFileHandlerTrips(fileHandlerData);

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each route found */
    GtfsFileReaderTrips tripsFileReader = (GtfsFileReaderTrips) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.TRIPS), getSettings().getInputSource());
    tripsFileReader.addHandler(tripsHandler);

    /* execute */
    tripsFileReader.read(StandardCharsets.UTF_8);
  }

  /**
   * Process GTFS calendars. Capture all service ids that fall within the selected day/time period, so we can filter
   * trips appropriately
   *
   * @param fileHandlerData containing all data to track and resources needed to perform the processing
   */
  private void processCalendars(GtfsServicesHandlerData fileHandlerData) {
    LOGGER.info("Processing: parsing GTFS Calendar...");

    /* due to overflow into the next day, we must accept all services from the preceding day as well and then determine
    if it matches
     * the stop times on the day after bearing this in mind */
    Predicate<GtfsCalendar> filterServiceIds = calRow ->
        calRow.isActiveOn(getSettings().getDayOfWeek()) || calRow.isActiveOn(getSettings().getDayOfWeek().minus(1));

    /* handler that will process individual calendar rows upon ingesting */
    var calendarHandler = new GtfsPlanitFileHandlerCalendar(fileHandlerData, filterServiceIds);

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each route found */
    GtfsFileReaderCalendars calendarFileReader = (GtfsFileReaderCalendars) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.CALENDARS), getSettings().getInputSource());
    calendarFileReader.addHandler(calendarHandler);

    /* execute */
    calendarFileReader.read(StandardCharsets.UTF_8);
  }

  /**
   * Process GTFS stops for their spatial scope only, i.e. establish which of them lie within the area the run covers.
   * <p>
   * Read ahead of the stop times that reference them, that being the only file linking a trip to its stops and so the
   * only point at which a trip can be told to lie wholly beyond the area rather than having been lost by the parser
   * </p>
   *
   * @param fileHandlerData containing all data to track and resources needed to perform the processing
   */
  private void processStopScope(GtfsServicesHandlerData fileHandlerData) {
    LOGGER.info("Processing: parsing GTFS stops for spatial scope...");

    /* handler that will process individual stops upon ingesting */
    var stopScopeHandler = new GtfsPlanitFileHandlerStopScope(fileHandlerData);

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each stop found */
    GtfsFileReaderStops stopsFileReader = (GtfsFileReaderStops) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.STOPS), getSettings().getInputSource());
    stopsFileReader.addHandler(stopScopeHandler);

    /* execute */
    stopsFileReader.read(StandardCharsets.UTF_8);
  }

  /**
   * Process GTFS routes. Capture modes of routes to use later on to identify supported mdoes for GTFS stops
   * which in turn are used to map to PLANit entities
   *
   * @param fileHandlerData containing all data to track and resources needed to perform the processing
   */
  private void processRoutes(GtfsServicesHandlerData fileHandlerData) {
    LOGGER.info("Processing: parsing GTFS Routes...");

    /* handler that will process individual routes upon ingesting */
    var routesHandler = new GtfsPlanitFileHandlerRoutes(fileHandlerData);

    /* GTFS file reader that parses the raw GTFS data and applies the handler to each route found */
    GtfsFileReaderRoutes routesFileReader = (GtfsFileReaderRoutes) GtfsReaderFactory.createFileReader(
        GtfsFileSchemeFactory.create(GtfsFileType.ROUTES), getSettings().getInputSource());
    routesFileReader.addHandler(routesHandler);

    /* execute */
    routesFileReader.read(StandardCharsets.UTF_8);
  }

  /**
   * Log some stats on the now available PLANit entities in memory
   */
  private void logPlanitStats(GtfsServicesHandlerData fileHandlerData) {

    fileHandlerData.getServiceNetwork().logInfo(
        LoggingUtils.serviceNetworkPrefix(fileHandlerData.getServiceNetwork().getId()));
    fileHandlerData.getRoutedServices().logInfo(
        LoggingUtils.routedServicesPrefix(fileHandlerData.getRoutedServices().getId()));

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
    /* meta-data for routes including its mode */
    processCalendars(fileHandlerData);
    /* meta-data for grouping of instances for a route via its service id */
    processTrips(fileHandlerData);
    /* spatial scope of the stops, needed while reading the stop times that reference them */
    processStopScope(fileHandlerData);
    /* matching routes and trips to stops at actual times */
    processStopTimes(fileHandlerData);
    /* the stop scope has served its purpose now that stop times are read */
    fileHandlerData.releaseGtfsStopScope();
    /* matching routes and trips to stops based on frequency information */
    processFrequencies(fileHandlerData);

    //TODO: option to convert schedules to frequency based approach

    /* due to time period based filtering it is possible that trips have just a single valid stop, meaning no single
    leg. These need to be removed */
    var gtfsTripIdsBeforeRemoval = collectGtfsTripIds(fileHandlerData.getRoutedServices());
    GtfsRoutedServicesModifierUtils.removeScheduledTripsWithoutLegs(fileHandlerData.getRoutedServices());
    registerRemovedGtfsTrips(fileHandlerData, gtfsTripIdsBeforeRemoval);

    /* due to routed being created beforehand without knowing what trips are eligible, routes can end up without
    having trips in the valid time period. These need to be removed */
    var gtfsRouteIdsBeforeRemoval = collectGtfsRouteIds(fileHandlerData.getRoutedServices());
    GtfsRoutedServicesModifierUtils.removeServiceRoutesWithoutTrips(fileHandlerData.getRoutedServices());
    registerRemovedGtfsRoutes(fileHandlerData, gtfsRouteIdsBeforeRemoval);
    /* due to removal of service routes, or some modes not being supported, it is possible entire modes no longer
    have any routes associated with them. These need to be removed */
    GtfsRoutedServicesModifierUtils.removeEmptyRoutedServices(fileHandlerData.getRoutedServices());

    registerGtfsEntitiesOutsideNetworkArea(fileHandlerData);

    /* optional optimisation/processing */
    if(getSettings().isGroupIdenticalGtfsTrips()){
      LOGGER.info("Optimising: Consolidating GTFS trip departures with identical relative schedules...");
      GtfsRoutedServicesModifierUtils.groupIdenticallyScheduledPlanitTrips(fileHandlerData.getRoutedServices());
    }

    /* due to grouping as well as the fact that GTFS is not perfect and may contain duplicate trips, we often
    see duplicate departure times occurring. these need to be removed */
    GtfsRoutedServicesModifierUtils.removeDuplicateTripDepartures(fileHandlerData.getRoutedServices());

    LOGGER.info("Processing: GTFS services Done");
  }

  /** Constructor where settings are directly provided such that input information can be extracted from it
   *
   * @param referenceNetwork to use
   * @param settings to use
   */
  protected GtfsServicesReader(MacroscopicNetwork referenceNetwork, final GtfsServicesReaderSettings settings) {
    this(IdGroupingToken.collectGlobalToken(), referenceNetwork, settings);
  }

  /** Constructor where settings are directly provided such that input information can be extracted from it
   * 
   * @param idToken to use for the routed services and service network ids
   * @param referenceNetwork to use
   * @param settings to use
   */
  protected GtfsServicesReader(
      final IdGroupingToken idToken, MacroscopicNetwork referenceNetwork, final GtfsServicesReaderSettings settings) {
    this.referenceNetwork = referenceNetwork;
    this.settings = settings;
    this.idToken = idToken;
  }  

  /**
   * {@inheritDoc}
   */
  @Override
  public Pair<ServiceNetwork, RoutedServices> read(){

    PlanItRunTimeException.throwIf(StringUtils.isNullOrBlank(getSettings().getCountryName()),
        "Country not set for GTFS services reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(getSettings().getInputSource(),
        "Input directory not set for GTFS services reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(referenceNetwork,
        "Reference network not available when parsing GTFS services, unable to proceed");

    /* prepare for parsing */
    var fileHandlerData = initialiseBeforeParsing();

    boolean valid = getSettings().validate();
    if(!valid){
      LOGGER.severe("GTFS routed services reader settings incomplete or invalid, unable to commence parsing");
      return null;
    }
    logSettings();

    /* main processing  */
    doMainProcessing(fileHandlerData);

    /* retain what became of the GTFS entities read, so it remains available to whoever drives this reader */
    this.rawGtfsEntityDiagnostics = fileHandlerData.getDiagnostics();

    /* log stats */
    fileHandlerData.getProfiler().logProcessingStats();
    logPlanitStats(fileHandlerData);

    if(reportCoverage){
      /* this reader is the whole parse, so what became of the feed is complete and can be reported */
      GtfsCoverageReport.report(
          this.rawGtfsEntityDiagnostics, null, getSettings().isPersistParseDiagnostics(),
          getSettings().getParseDiagnosticsOutputDirectory());
    }

    /* return parsed GTFS services in PLANit memory model form*/
    return Pair.of(fileHandlerData.getServiceNetwork(), fileHandlerData.getRoutedServices());
  }

  /**
   * GTFS Services are ingested and lead to PLANit service nodes to be created based on GTFS stop ids. When at some
   * later point in time these PLANit service nodes are to be linked to PLANit transfer zones (which in turn have an
   * association with a GTFS stop) the mapping between PLANit service node and its underlying GTFS stop needs to
   * remain available. This function provides this mapping.
   * <p>
   *   For now this mapping is purely based on the external id, but if this changes using this explicit functional
   *   approach allows us to change this without having to change the process flow itself
   * </p>
   *
   * @return mapping from PLANit service node to underlying source GTFS stop id
   */
  public Function<ServiceNode, String> getServiceNodeToGtfsStopIdMapping(){
    return GtfsServicesHandlerData.getServiceNodeToGtfsStopIdMapping();
  }

  /**
   * Collect what became of the GTFS entities read, only available once reading completed
   *
   * @return diagnostics, null when reading has yet to take place
   */
  public GtfsParseDiagnostics getRawGtfsEntityDiagnostics() {
    return rawGtfsEntityDiagnostics;
  }

  /**
   * Stop this reader reporting what became of the feed when it completes, for a caller that drives it as one stage of
   * a wider parse and reports on all of them itself once they have run
   */
  void suppressCoverageReport() {
    this.reportCoverage = false;
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
  }

}
