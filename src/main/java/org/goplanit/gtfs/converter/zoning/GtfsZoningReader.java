package org.goplanit.gtfs.converter.zoning;

import org.goplanit.converter.zoning.ZoningReader;
import org.goplanit.graph.modifier.event.handler.SyncXmlIdToIdBreakEdgeHandler;
import org.goplanit.graph.directed.modifier.event.handler.SyncXmlIdToIdBreakEdgeSegmentHandler;
import org.goplanit.gtfs.converter.zoning.handler.GtfsPlanitFileHandlerStops;
import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerData;
import org.goplanit.gtfs.converter.zoning.handler.GtfsZoningHandlerProfiler;
import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.reader.GtfsFileReaderStops;
import org.goplanit.gtfs.reader.GtfsReaderFactory;
import org.goplanit.gtfs.scheme.GtfsFileSchemeFactory;
import org.goplanit.network.MacroscopicNetworkModifierUtils;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.zoning.Zoning;
import org.goplanit.zoning.ZoningModifierUtils;

import java.nio.charset.StandardCharsets;
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
    if(zoning.getTransferConnectoids().isEmpty()){
      LOGGER.info("PLANit Zoning has no transfer zone connectoids, creating new transfer zones for each eligible GTFS stop");
    }else{
      LOGGER.info("PLANit Zoning has transfer zone connectoids, fusing existing transfer zones with GTFS stops where possible");
    }
    getSettings().logSettings();
  }

  /**
   * Initialise event listeners used to inject functionality to the modifiers when modifications are made to the zoning and/or network. For example
   * by syncing XML ids to internal ids when we break links
   */
  private void syncIdsAndinitialiseEventListeners() {
    /** listener with functionality to sync XML ids to unique internal id upon breaking a link, ensures that when persisting
     *  physical network by XML id,  we do not have duplicate ids */
    SyncXmlIdToIdBreakEdgeHandler syncXmlIdToIdOnBreakLink = new SyncXmlIdToIdBreakEdgeHandler();

    /** listener with functionality to sync XML ids to unique internal id upon breaking a link segment, ensures that when persisting
     * physical network by XML id,  we do not have duplicate ids */
    SyncXmlIdToIdBreakEdgeSegmentHandler syncXmlIdToIdOnBreakLinkSegment = new SyncXmlIdToIdBreakEdgeSegmentHandler();

    /* network layers */
    var referenceNetwork = this.serviceNetwork.getParentNetwork();
    for(MacroscopicNetworkLayer networkLayer : referenceNetwork.getTransportLayers()){
      var layerModifier = networkLayer.getLayerModifier();
      layerModifier.removeAllListeners();

      /* whenever a link(segment) is broken we ensure that its XML id is synced with the internal id to ensure it remains unique */
      layerModifier.addListener(syncXmlIdToIdOnBreakLink);
      layerModifier.addListener(syncXmlIdToIdOnBreakLinkSegment);
    }

    // update all network XML ids to internal id's upon calling recreating managed id entities on a graph layer
    LOGGER.info("Syncing PLANit network XML ids to internal ids");
    MacroscopicNetworkModifierUtils.syncManagedIdEntitiesContainerXmlIdsToIds(referenceNetwork);

    /* zoning: since zoning can be partially populated we must ensure we do not generate XML ids synced to internal ids that clash with
    * pre-existing XML ids, hence recreated managed ids and sync all XML ids to internal ids as well */
    LOGGER.info("Syncing PLANit zoning XML ids to internal ids");
    ZoningModifierUtils.syncManagedIdEntitiesContainerXmlIdsToIds(zoning);
  }

  /**
   * perform final preparation before conducting parsing of GTFS pt entities
   */
  private GtfsZoningHandlerData initialiseBeforeParsing() {
    // sync crs of zoning to network if not set
    if(zoning.getCoordinateReferenceSystem()==null){
      zoning.setCoordinateReferenceSystem(this.serviceNetwork.getParentNetwork().getCoordinateReferenceSystem());
    }

    // initialise data
    readInvoked = false;
    syncIdsAndinitialiseEventListeners();
    return new GtfsZoningHandlerData(getSettings(), zoning, serviceNetwork, routedServices, new GtfsZoningHandlerProfiler());
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
        GtfsFileSchemeFactory.create(GtfsFileType.STOPS), getSettings().getInputSource());
    stopsFileReader.addHandler(stopsHandler);

    /* configuration of reader */
    stopsFileReader.getSettings().excludeColumns(GtfsKeyType.STOP_CODE);
    stopsFileReader.getSettings().excludeColumns(GtfsKeyType.STOP_URL);
    stopsFileReader.getSettings().excludeColumns(GtfsKeyType.STOP_TIMEZONE);
    stopsFileReader.getSettings().excludeColumns(GtfsKeyType.WHEELCHAIR_BOARDING);

    /* execute */
    stopsFileReader.read(StandardCharsets.UTF_8);
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
    PlanItRunTimeException.throwIfNull(getSettings().getInputSource(), "Input source not set for GTFS zoning reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(serviceNetwork.getParentNetwork(),"Reference physical network not available when parsing GTFS zoning, unable to proceed");

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
