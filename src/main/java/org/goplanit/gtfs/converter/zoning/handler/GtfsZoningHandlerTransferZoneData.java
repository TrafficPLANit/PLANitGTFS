package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.index.quadtree.Quadtree;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Zoning handler data specifically tailored towards transfer zones
 *
 * @author markr
 */
public class GtfsZoningHandlerTransferZoneData {

  /**
   * Function to hide implementation of mapping between GTFS Stop id and transfer zone
   *
   * @author markr
   */
  public class GtfsStopIdToTransferZone implements Function<String, TransferZone> {

    private final Map<String, TransferZone> mappedTransferZonesByGtfsStopId;

    /** Constructor
     * @param mappedTransferZonesByGtfsStopId containing the mapping two wrap*/
    private GtfsStopIdToTransferZone(Map<String, TransferZone> mappedTransferZonesByGtfsStopId){
      this.mappedTransferZonesByGtfsStopId = mappedTransferZonesByGtfsStopId;
    }

    @Override
    public TransferZone apply(String gtfsStopId) {
      return mappedTransferZonesByGtfsStopId.get(gtfsStopId);
    }
  }

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningHandlerTransferZoneData.class.getCanonicalName());

  /** Track all registered/mapped transfer zones by their GTFS stop id */
  private Map<String, TransferZone> mappedTransferZonesByGtfsStopId;

  /** track all supported pt service modes for (partly pre-existing) PLANit transfer zones that have and are to be created and
   * their used directed connectoids so we can pinpoint PT stop locations on the physical road network more accurately rather than
   * relying on the location of the transfer zone (pole, platform) which might cause mismatches compared to GTFS STOP locations */
  private Map<TransferZone,Set<DirectedConnectoid>> transferZoneConnectoidIndex;

  /** track existing transfer zones present geo spatially to be able to fuse with GTFS data when appropriate */
  private Quadtree geoIndexPreExistingTransferZones;

  /** track existing transfer zones by their external id to be able to fuse with GTFS data when manually overwritten by user */
  private Map<String, TransferZone> preExistingTransferZonesByExternalId;

  /** track all GTFS stops that have been mapped to pre-existing transfer zones. We do so, to allow for correcting earlier
   * matches due to - for example - a transfer zone based on OSM was not complete and should be split in two, e.g. there are stops
   * on both sides of the road, but OSM only contains a stop on one side. In that case we must be able to retrieve the earlier mapped GTFS stop
   * and decide how to proceed
   */
  private Map<String, GtfsStop> mappedGtfsStops;

  /** initialise data tracking containers
   *
   * @param settings to use
   * @param zoning to use
   */
  private void initialise(GtfsZoningReaderSettings settings, Zoning zoning){
    this.mappedTransferZonesByGtfsStopId = new HashMap<>();
    this.transferZoneConnectoidIndex = new HashMap<>();
    this.mappedGtfsStops = new HashMap<>();

    // geo indexed existing transfer zones
    this.geoIndexPreExistingTransferZones = GeoContainerUtils.toGeoIndexed(zoning.getTransferZones());
    // external id indexed existing transfer zones (relying on single and unique external id per transfer zone!),
    // used for quickly finding overwritten mappings between GTFS stops and existing transfer zones
    this.preExistingTransferZonesByExternalId = zoning.getTransferZones().toMap(tz-> tz.getExternalId());

    /* index: MODE <-> (pre-existing) TRANSFER ZONE */
    if(!zoning.getTransferConnectoids().isEmpty()){
      /* derive mode support for each transfer zone based on its connectoid (segments) modes. Used to improve matching of GTFS stops to existing
       * stops in the provided network/zoning */
      var connectoidsByAccessZone = zoning.getTransferConnectoids().createIndexByAccessZone();
      for(var entry :connectoidsByAccessZone.entrySet()){
        if(entry.getKey() instanceof TransferZone){
          var transferZone = (TransferZone) entry.getKey();
          for(var dirConnectoid : entry.getValue()){
            /* register on transfer zone */
            registerTransferZoneToConnectoidModes(transferZone,dirConnectoid, settings.getAcivatedPlanitModes());
          }
        }
      }
    }

  }

  /**
   * Constructor
   * @param settings to use
   * @param referenceZoning to use
   */
  public GtfsZoningHandlerTransferZoneData(GtfsZoningReaderSettings settings, Zoning referenceZoning){
    initialise(settings, referenceZoning);
  }

  /**
   * Reset the PLANit data tracking containers
   */
  public void reset() {
    mappedTransferZonesByGtfsStopId.clear();
    transferZoneConnectoidIndex.clear();
    geoIndexPreExistingTransferZones = null;
    preExistingTransferZonesByExternalId.clear();
  }

  /**
   * Register transfer as mapped to a GTFS stop, index it by its GtfsStopId, and register the stops mode as supported
   * on the PLANit transfer zone (if not already present)
   *
   * @param gtfsStop to register on PLANit transfer zone
   * @param transferZone to register one
   * @return true when already mapped by GTFS stop, false otherwise
   */
  public void registerMappedGtfsStop(GtfsStop gtfsStop, TransferZone transferZone) {
    var oldZone = mappedTransferZonesByGtfsStopId.put(gtfsStop.getStopId(), transferZone);
    if(oldZone != null && !oldZone.equals(transferZone)) {
      throw new PlanItRunTimeException("Multiple transfer zones found for the same GTFS STOP_ID %s, this is not yet supported", gtfsStop.getStopId());
    }

    var oldStop = mappedGtfsStops.put(gtfsStop.getStopId(), gtfsStop);
    if(oldStop != null && !oldStop.equals(gtfsStop)) {
      throw new PlanItRunTimeException("Multiple GTFS stops found for the same GTFS STOP_ID %s, this is not yet supported", gtfsStop.getStopId());
    }
  }

  /**
   * Get the transfer zone that the GTFS stop was already mapped to (if any)
   *
   * @param gtfsStop to use
   * @return PLANit transfer zone it is mapped to, null if no mapping exists yet
   */
  public TransferZone getMappedTransferZone(GtfsStop gtfsStop){
    return mappedTransferZonesByGtfsStopId.get(gtfsStop.getStopId());
  }

  /**
   * Check if transfer zone already has a mapped GTFS stop
   * @param transferZone to check
   * @return true when already mapped by GTFS stop, false otherwise
   */
  public boolean hasMappedGtfsStop(TransferZone transferZone) {
    return mappedTransferZonesByGtfsStopId.containsValue(transferZone);
  }

  /**
   * Retrieve a GTFS stop that has been mapped to a pre-existing PLANit transfer zone
   *
   * @param gtfsStopId to use
   * @return found GTFS stop (if any)
   */
  public GtfsStop getMappedGtfsStop(String gtfsStopId) {
    return mappedGtfsStops.get(gtfsStopId);
  }

  /**
   * The pt services modes supported on the given transfer zone
   *
   * @param planitTransferZone to get supported pt service modes for
   * @param modesFilter to select from
   * @return found PLANit modes
   */
  public Set<Mode> getSupportedPtModesIn(TransferZone planitTransferZone, Set<Mode> modesFilter){
    var ptConnectoids = transferZoneConnectoidIndex.get(planitTransferZone);
    Set<Mode> ptServiceModes = new HashSet<>();
    for(var connectoid : ptConnectoids) {
      ptServiceModes.addAll(((MacroscopicLinkSegment) connectoid.getAccessLinkSegment()).getAllowedModesFrom(modesFilter));
    }
    return ptServiceModes;
  }

  /**
   * Update registered and activated pt modes and their access information on transfer zone
   *
   * @param transferZone        to update for
   * @param directedConnectoid  to extract access information from
   * @param activatedPlanitModes supported modes
   */
  public void registerTransferZoneToConnectoidModes(TransferZone transferZone, DirectedConnectoid directedConnectoid, Set<Mode> activatedPlanitModes) {
    var allowedModes = ((MacroscopicLinkSegment) directedConnectoid.getAccessLinkSegment()).getAllowedModes();

    /* remove all non service modes */
    allowedModes.retainAll(activatedPlanitModes);
    if(allowedModes.isEmpty()){
      return;
    }

    /* at least one activated PT service mode present on connectoid, register it */
    transferZoneConnectoidIndex.putIfAbsent(transferZone, new HashSet<>());
    transferZoneConnectoidIndex.get(transferZone).add(directedConnectoid);
  }

  /**
   * Connectoids related to Pt activated modes available for this transfer zone
   * @param transferZone to extract for
   * @return known connectoids
   */
  public Set<DirectedConnectoid> getTransferZoneConnectoids(TransferZone transferZone) {
    return transferZoneConnectoidIndex.get(transferZone);
  }

  /**
   * Get all the geo indexed transfer zones as a quad tree
   *
   * @return registered geo indexed transfer zones
   */
  public Quadtree getGeoIndexedPreExistingTransferZones() {
    return this.geoIndexPreExistingTransferZones;
  }

  /**
   * Get all the existing transfer zones by their external id
   *
   * @return existing transfer zones by external id
   */
  public Map<String, TransferZone> getPreExistingTransferZonesByExternalId() {
    return preExistingTransferZonesByExternalId;
  }

  /**
   * Create mapping function while hiding how the mapping is stored
   *
   * @return function that can map GTFS stop ids to transfer zones based on internal state of this data tracker
   */
  public Function<String, TransferZone> createGtfsStopToTransferZoneMappingFunction() {
    return new GtfsStopIdToTransferZone(this.mappedTransferZonesByGtfsStopId);
  }

}
