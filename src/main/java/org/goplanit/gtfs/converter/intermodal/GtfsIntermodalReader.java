package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.converter.PairConverterReader;
import org.goplanit.converter.intermodal.IntermodalReader;
import org.goplanit.gtfs.converter.diagnostics.GtfsCoverageReport;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityDiagnostics;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityIssue;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityType;
import org.goplanit.gtfs.converter.service.GtfsServicesReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderFactory;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderFactory;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.util.GtfsRoutedServicesModifierUtils;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.service.routed.modifier.event.handler.SyncDeparturesXmlIdToIdHandler;
import org.goplanit.service.routed.modifier.event.handler.SyncRoutedServicesXmlIdToIdHandler;
import org.goplanit.service.routed.modifier.event.handler.SyncRoutedTripsXmlIdToIdHandler;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Quadruple;
import org.goplanit.utils.service.routed.modifier.RoutedServicesModifierListener;
import org.goplanit.zoning.Zoning;
import org.goplanit.zoning.ZoningModifierUtils;

import java.util.List;
import java.util.logging.Logger;

/**
 * GTFS intermodal reader. Supplements an already populated network and (partially populated) zoning with GTFS services
 * resulting in a consistent service network and routed services PLANit memory model. In case the provided zoning
 * already contains transfer zones an attempt is made to fuse the GTFS stops with existing transfer zones when
 * applicable, in case this is not possible or such zones are absent altogether they will be injected from scratch.
 * 
 * @author markr
 *
 */
public class GtfsIntermodalReader implements IntermodalReader<ServiceNetwork, RoutedServices> {
  
  /** intermodal reader settings to use */
  protected final GtfsIntermodalReaderSettings settings;

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesReader.class.getCanonicalName());

  /** id token to use */
  private IdGroupingToken idToken;

  /** network to use (unless obtained from networkAndZoningReader) */
  private MacroscopicNetwork parentNetwork;

  /** zoning to use (unless obtained from networkAndZoningReader) */
  private Zoning parentZoning;

  /** reader to use to extract network and zoning (unless obtained from network and zoning instances ) */
  private PairConverterReader<MacroscopicNetwork, Zoning> networkAndZoningReader;

  /** what became of the GTFS entities read across all stages, available once reading completed */
  private GtfsParseDiagnostics rawGtfsEntityDiagnostics;

  /** what became of the PLANit entities derived from the feed, available once reading completed */
  private GtfsPlanitEntityDiagnostics planitEntityDiagnostics;

  /* Remove all routes/services that fall outside the physical network's bounding box, i.e., remained unmapped and
   * therefore have no mapping populated with respect to their parent service network (and physical network).
   * Sync XML ids to avoid gaps or potentially duplicate usage of these ids
   *
   *@param routedServices to check
   */
  private void truncateToServiceNetwork(RoutedServices routedServices) {
    List<RoutedServicesModifierListener> listeners =
        List.of(new SyncDeparturesXmlIdToIdHandler(), new SyncRoutedServicesXmlIdToIdHandler(),
                new SyncRoutedTripsXmlIdToIdHandler());

    for( var layer : routedServices.getLayers()){
      listeners.forEach( l -> layer.getLayerModifier().addListener(l));

      /* execute with listeners in place */
      layer.getLayerModifier().truncateToServiceNetwork();

      listeners.forEach( l -> layer.getLayerModifier().removeListener(l));
    }
  }

  /** Constructor where settings are directly provided such that input information can be extracted from it
   *
   * @param idToken to use for the routed services and service network ids
   * @param parentNetwork to use
   * @param parentZoning to use
   * @param settings to use
   */
  protected GtfsIntermodalReader(
          final IdGroupingToken idToken,
          MacroscopicNetwork parentNetwork,
          final Zoning parentZoning,
          final GtfsIntermodalReaderSettings settings){

    this.settings = settings;

    this.idToken = idToken;
    this.networkAndZoningReader = null;
    this.parentNetwork = parentNetwork;
    this. parentZoning =  parentZoning;

  }

  /** Constructor where settings are directly provided such that input information can be extracted from it,
   * use reader to obtain network and zoning instances.
   *
   * @param networkAndZoningReader to use
   * @param settings to use
   */
  protected GtfsIntermodalReader(
          final PairConverterReader<MacroscopicNetwork, Zoning> networkAndZoningReader,
          final GtfsIntermodalReaderSettings settings){

    this.settings = settings;

    this.idToken = null;
    this.networkAndZoningReader = networkAndZoningReader;
    this.parentNetwork = null;
    this.parentZoning =  null;
  }



  /**
   * GTFS intermodal reader - when used - only supports reading with services included. Hence, calling this method
   * will log this to the user and will not generate any results.
   *
   * @return always null
   */
  @Override
  public Pair<MacroscopicNetwork, Zoning> read() {
    LOGGER.warning("GTFS Intermodal Reader only supports readWithServices(), read() is not supported due to " +
            "absence of physical network in GTFS data...");
    LOGGER.warning("...To parse a compatible network consider using the OSM Intermodal Reader or another " +
            "compatible network reader to pre-load the network");
    return null;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
    rawGtfsEntityDiagnostics = null;
    planitEntityDiagnostics = null;
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
   * Collect what became of the PLANit entities derived from the feed, only available once reading completed
   *
   * @return diagnostics, null when reading has yet to take place
   */
  public GtfsPlanitEntityDiagnostics getPlanitEntityDiagnostics() {
    return planitEntityDiagnostics;
  }

  /**
   * {@inheritDoc}
   */    
  @Override
  public GtfsIntermodalReaderSettings getSettings() {
    return this.settings;
  }

  /**
   * GTFS intermodal reader supports reading with services.
   * @return true
   */
  @Override
  public boolean supportServiceConversion() {
    return true;
  }

  /**
   * Perform the conversion and parsing into PLANit memory model with service network and services
   *
   * @return created and or further populated network, zoning, service network and services
   */
  @Override
  public Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> readWithServices() {

    if( (parentNetwork==null || parentZoning==null) && networkAndZoningReader==null){
      LOGGER.severe("GTFS intermodal reader incorrectly configured, no network and/or zoning available, " +
              "yet no reader present to construct them beforehand, this shouldn't happen");
      return null;
    }

    if(networkAndZoningReader!=null){
      var networkAndZoning = networkAndZoningReader.read();
      if(networkAndZoning==null || networkAndZoning.anyIsNull()){
        LOGGER.severe("Unable to construct network and/or zoning for GTFS intermodal reader");
        return null;
      }
      parentNetwork = networkAndZoning.first();
      parentZoning = networkAndZoning.second();
      this.idToken = parentNetwork.getIdGroupingToken();
    }

    /* SERVICES without geo filter (since locations are currently only parsed when considering GTFS stops via
     * (transfer) zoning reader), hence all routes/services are initially mapped to PLANit equivalents but without
     * mapping to physical network yet */
    GtfsServicesReader servicesReader =
            GtfsServicesReaderFactory.createWithoutCoverageReport(
                parentNetwork, getSettings().getServiceSettings());
    Pair<ServiceNetwork,RoutedServices> servicesResult = servicesReader.read();

    /* ZONING (PT stops as transfer zones) this parses GTFS stops and their locations, parsed GTFS stops are
     * constrained to bounding box of underlying physical network */
    final var zoningReader = GtfsZoningReaderFactory.createWithoutCoverageReport(
        getSettings().getZoningSettings(),
        parentZoning,
        servicesResult.first(),
        servicesResult.second(),
        servicesReader.getServiceNodeToGtfsStopIdMapping());
    var zoning = zoningReader.read();

    /* INTEGRATE: integrate the zoning, service network and network by finding paths between the identified stops
    for all given services, for now, we generate the paths based on simple Dijkstra shortest paths, in the future
    more sophisticated alternatives could be used */
    var integrator = new GtfsServicesAndZoningReaderIntegrator(
        settings,
        zoning,
        servicesResult.first(),
        servicesResult.second(),
        servicesReader.getServiceNodeToGtfsStopIdMapping(),
        zoningReader.getGtfsStopIdToTransferZoneMapping(),
        /* the stops were read by the zoning stage, so what became of each of them is recorded in its account */
        gtfsStopId -> zoningReader.getRawGtfsEntityDiagnostics().getDiscardIssue(GtfsObjectType.STOP, gtfsStopId));
    integrator.execute();

    /* DIAGNOSTICS: each stage recorded its own account, none of which says much on its own. Bring them together here,
     * this being the only place that sees all of them, and add what the clean-up below takes away again */
    this.rawGtfsEntityDiagnostics = servicesReader.getRawGtfsEntityDiagnostics().newEmptyInstance();
    this.rawGtfsEntityDiagnostics.merge(servicesReader.getRawGtfsEntityDiagnostics());
    this.rawGtfsEntityDiagnostics.merge(zoningReader.getRawGtfsEntityDiagnostics());
    this.planitEntityDiagnostics = integrator.getPlanitEntityDiagnostics();
    var cleanUpDiagnostics = new GtfsCleanUpDiagnostics(this.planitEntityDiagnostics);

    /* the stops behind each trip, taken before any clean up detaches the legs that name them */
    var gtfsStopIdsBySchedule = GtfsCleanUpDiagnostics.captureGtfsStopIdsBySchedule(
        servicesResult.second(), servicesReader.getServiceNodeToGtfsStopIdMapping());

    /* SERVICE NETWORK CLEAN-UP */
    {
      var serviceNodesBefore = GtfsCleanUpDiagnostics.collectServiceNodes(servicesResult.first());

      /* CLEAN-UP: remove all routes/services that fall outside the physical network's bounding box, i.e.,
       * remained unmapped */
      servicesResult.first().getTransportLayers().forEach(
              l -> l.getLayerModifier().removeUnmappedServiceNetworkEntities());

      cleanUpDiagnostics.registerRemoved(
          GtfsPlanitEntityType.SERVICE_NODE, GtfsPlanitEntityIssue.SERVICE_NODE_OUTSIDE_NETWORK_AREA,
          serviceNodesBefore, GtfsCleanUpDiagnostics.collectServiceNodes(servicesResult.first()));
    }

    /* ROUTED SERVICES CLEAN-UP */
    {
      var routedServicesBefore = GtfsCleanUpDiagnostics.collectRoutedServices(servicesResult.second());
      var tripSchedulesBefore = GtfsCleanUpDiagnostics.collectTripSchedules(servicesResult.second());

      /* what the truncation will make of each schedule, taken now because it clears away what it removes and the
       * service network it is judged against has already been cut back to what the physical network covers */
      var truncationOutcomes = GtfsCleanUpDiagnostics.classifyTruncationOutcomes(
          servicesResult.second(), this.rawGtfsEntityDiagnostics, gtfsStopIdsBySchedule);

      /* CLEAN-UP: remove all routes/services that fall outside the physical network's bounding box, i.e.,
       * remained unmapped and therefore have no mapping populated with respect to their parent service network
       * (and physical network) */
      truncateToServiceNetwork(servicesResult.second());

      cleanUpDiagnostics.registerTruncated(
          GtfsPlanitEntityType.ROUTED_TRIP_SCHEDULE,
          tripSchedulesBefore, GtfsCleanUpDiagnostics.collectTripSchedules(servicesResult.second()),
          truncationOutcomes::get);

      /* the same truncation takes the service along with the last of its trips, which is a loss of its own: a route
       * that reached the area and left no service behind is not accounted for by what befell any single trip of it */
      cleanUpDiagnostics.registerRemoved(
          GtfsPlanitEntityType.ROUTED_SERVICE, GtfsPlanitEntityIssue.ROUTED_SERVICE_WITHOUT_TRIPS,
          routedServicesBefore, GtfsCleanUpDiagnostics.collectRoutedServices(servicesResult.second()));

      /* CLEAN-UP: optional optimisation/processing. Note while we already did this in the services reader as well,
       * due to the above truncation trips have been altered and as a result more trips will now have identical
       * leg timings and therefore can be grouped further */
      if (getSettings().getServiceSettings().isGroupIdenticalGtfsTrips()) {
        LOGGER.info("Optimising: Consolidating remaining GTFS trip departures with identical " +
                "relative schedules...");
        GtfsRoutedServicesModifierUtils.groupIdenticallyScheduledPlanitTrips(servicesResult.second());
      }
      /* CLEAN-UP: Due to grouping as well as the fact that GTFS is not perfect and may contain duplicate trips, we
       * often see duplicate departure times occurring. these need to be removed */
      var departuresBefore = GtfsCleanUpDiagnostics.collectDepartures(servicesResult.second());
      GtfsRoutedServicesModifierUtils.removeDuplicateTripDepartures(servicesResult.second());

      cleanUpDiagnostics.registerRemoved(
          GtfsPlanitEntityType.DEPARTURE, GtfsPlanitEntityIssue.DEPARTURE_DUPLICATE_SCHEDULED_TIME,
          departuresBefore, GtfsCleanUpDiagnostics.collectDepartures(servicesResult.second()));
    }

    /* ZONING CLEAN-UP */
    {
      if(getSettings().getZoningSettings().isRemoveUnusedTransferZones()){
        //todo: verify the services are correctly updated and use the right zone ids etc.!

        /* first remove the unused connectoids */
        zoning.getZoningModifier().removeUnusedTransferConnectoids(
                servicesResult.first().getTransportLayers(), false);
        /* then we can remove transfer zones without connectoids */
        zoning.getZoningModifier().removeDanglingTransferZones(false);
        /* then we can remove transfer zone groups without transfer zones */
        zoning.getZoningModifier().removeDanglingTransferZoneGroups(false);
        // --> now recreate ids
        ZoningModifierUtils.updateAndSyncManagedIdEntitiesContainerXmlIdsToIds(zoning);
      }
    }

    /* log final result */
    LOGGER.info("Final result Stats:");
    parentNetwork.logInfo(LoggingUtils.networkPrefix(parentNetwork.getId()));
    zoning.logInfo(LoggingUtils.zoningPrefix(zoning.getId()));
    servicesResult.first().logInfo(LoggingUtils.serviceNetworkPrefix(servicesResult.first().getId()));
    servicesResult.second().logInfo(LoggingUtils.routedServicesPrefix(servicesResult.second().getId()));

    /* every stage has run and the result is final, so what became of the feed can be reported */
    GtfsCoverageReport.report(
        this.rawGtfsEntityDiagnostics, this.planitEntityDiagnostics, getSettings().isPersistParseDiagnostics(),
        getSettings().getParseDiagnosticsOutputDirectory());

    /* combined result */
    return Quadruple.of(parentNetwork, zoning, servicesResult.first(), servicesResult.second());
  }

}
