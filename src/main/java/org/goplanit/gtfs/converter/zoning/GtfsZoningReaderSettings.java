package org.goplanit.gtfs.converter.zoning;

import org.goplanit.converter.idmapping.IdMapperType;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettings;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettingsWithModeMapping;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.locationtech.jts.geom.Coordinate;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Capture all the user configurable settings regarding how to
 * parse (if at all) (public transport) transfer infrastructure such as stations, poles, platforms, and other
 * stop and transfer related infrastructure captured from raw (static) GTFS feeds
 * 
 * @author markr
 *
 */
public class GtfsZoningReaderSettings extends GtfsConverterReaderSettingsWithModeMapping implements GtfsConverterReaderSettings {

  /**
   * logger to use
   */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningReaderSettings.class.getCanonicalName());

  // Optional configuration settings

  /**
   * search radius used when mapping GTFS stops to PLANit transfer zones
   */
  private double gtfsStop2TransferZoneSearchRadiusMeters = DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS;

  /**
   * search radius used when mapping GTFS stops to PLANit road network, which given that GTFS stop is the vehicle stop location, should be less than distance to pole
   */
  private double gtfsStop2RoadSearchRadiusMeters = DEFAULT_GTFSSTOP_LINK_SEARCH_METERS;

  /**
   * Provide explicit mapping from GTFS stop (by GTFS stop id) to existing PLANit transfer zone(s) based on an id (XML or external id (third party source ide.g., OSM id)),
   * This overrides the parser's mapping functionality and maps the GTFS stop to this entity without further checking.
   */
  private final Map<String, List<Pair<Object, IdMapperType>>> overwriteGtfsStopTransferZoneExternalIdMapping = new HashMap<>();

  /**
   * Provide explicit mapping from GTFS stop (by GTFS stop id) to a geo-location.
   * This overrides the original geolocation of the stop.
   */
  private final Map<String, Coordinate> overwriteGtfsStopLocationMapping = new HashMap<>();

  /**
   * Indicate disallowing certain GTFS stops to be jointly mapped to the same transfer zone. In such cases, new (disjoint) transfer zones
   * will be created
   */
  private final Set<String> disallowGtfsTop2TransferZoneJointMapping = new HashSet<>();

  /**
   * Indicate to not match a GTFS stop to any existing transfer zones in PLANit network, but instead always create a new transfer zone
   */
  private final Set<String> forceCreateNewTransferZoneForGtfsStops = new HashSet<>();

  /**
   * track used excluded GTFS stop ids
   */
  private final Set<String> excludeGtfsStopsById = new HashSet<>();

  /**
   * track overwritten link mappings for GTFS stop ids
   */
  private final Map<String, Pair<Object, IdMapperType>> overwriteGtfsStop2LinkMapping = new HashMap<>();

  /**
   * Log to use the PLANit link a GTFS stop is mapped to (mostly for debugging purposes)
   */
  private final Set<String> logGtfsStop2PlanitLinkMapping = new HashSet<>();

  /**
   * flag to indicate if transfer zones that have no services stopping after parsing is complete, are to be removed or not
   */
  private boolean removeUnusedTransferZones = DEFAULT_REMOVE_UNUSED_TRANSFER_ZONES;

  /**
   * flag indicating if parser should log all GTFS zones that are mapped to existing PLANit  transfer zones
   */
  private boolean logMappedGtfsZones = DEFAULT_LOG_MAPPED_GTFS_ZONES;

  /**
   * flag indicating if parser should log all GTFS stops that triggered the creation of a new PLANit  transfer zone
   */
  private boolean logCreatedGtfsZones = DEFAULT_LOG_CREATED_GTFS_ZONES;

  /** track extended logging on how particular GTFS stops are being created and/or matched to an existing PLANit transferzone */
  private Set<String> extendedLoggingByGtfsStopId = new HashSet<>();

  /**
   * default flag setting whether to remove unused transfer zones after GTFS parsing is complete
   */
  public static final boolean DEFAULT_REMOVE_UNUSED_TRANSFER_ZONES = true;

  /**
   * default search radius for mapping GTFS stops to PLANit transfer zones
   */
  public static final double DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS = 40.0;

  /**
   * default search radius for mapping GTFS stops to PLANit links for determining stop locations on road/rail/water
   */
  public static final double DEFAULT_GTFSSTOP_LINK_SEARCH_METERS = 40.0;

  /**
   * default setting for logging mappings between GTFS zones and existing PLANit transfer zones
   */
  public static final boolean DEFAULT_LOG_MAPPED_GTFS_ZONES = false;

  /**
   * default setting for logging the creation of new transfer zones based and GTFS stops
   */
  public static final boolean DEFAULT_LOG_CREATED_GTFS_ZONES = false;


  /**
   * The default buffer distance when looking for links within a distance of the closest link to a GTFS stop to create connectoids (stop_locations).
   * In case candidates are so close just selecting the closest can lead to problems. By identifying multiple candidates via this buffer, we can then use more sophisticated ways than proximity
   * to determine the best candidate
   */
  public static double DEFAULT_CLOSEST_LINK_SEARCH_BUFFER_DISTANCE_M = 8;

  /**
   * Copy constructor creating a shallow copy of the underlying mode mapping so it is synced with the provided settings. Useful when both settings are used
   * in conjunction and we want to avoid having to sync information
   *
   * @param settings to obtain mode mapping information from
   */
  public GtfsZoningReaderSettings(GtfsConverterReaderSettingsWithModeMapping settings) {
    super(settings);
  }

  /**
   * Search radius in meters to map a GTFS stop location to an existing PLANit transfer zone
   *
   * @return searchRadiusMeters being applied
   */
  public double getGtfsStopToTransferZoneSearchRadiusMeters() {
    return this.gtfsStop2TransferZoneSearchRadiusMeters;
  }

  /**
   * Search radius in meters to map a GTFS stop location to an existing PLANit transfer zone
   *
   * @param searchRadiusMeters to apply
   */
  public void setGtfsStopToTransferZoneSearchRadiusMeters(double searchRadiusMeters) {
    this.gtfsStop2TransferZoneSearchRadiusMeters = searchRadiusMeters;
  }

  /**
   * Search radius in meters to map a GTFS stop location to an existing PLANit link (for its stop location)
   *
   * @return searchRadiusMeters being applied
   */
  public double getGtfsStopToLinkSearchRadiusMeters() {
    return this.gtfsStop2RoadSearchRadiusMeters;
  }

  /**
   * Search radius in meters to map a GTFS stop location to an existing PLANit link (for its stop location)
   *
   * @param searchRadiusMeters to apply
   */
  public void setGtfsStopToLinkSearchRadiusMeters(double searchRadiusMeters) {
    this.gtfsStop2RoadSearchRadiusMeters = searchRadiusMeters;
  }


  /**
   * Provide the mapping from a PLANit service node in the service network to its GTFS STOP ID
   *
   * @return function mapping to perform conversion from a given service node to GTFS STOP ID
   */
  public Function<ServiceNode, String> getServiceNodeToGtfsStopIdFunction() {
    return GtfsServicesReaderSettings.getServiceNodeToGtfsStopIdMapping();
  }

  /**
   * Provide explicit mapping for GTFS stop id to an existing PLANit transfer zone, e.g., platform, pole, station, halt, stop, etc. (by its external id, e.g. OSM id)
   * This overrides the parser's mapping functionality and immediately maps the stop to this entity. Can be useful to avoid warnings or wrong mapping of
   * stop locations in case the automated behaviour does not perform as expected.
   * <p>
   * It also allows one to map a GTFS stop to multiple transfer zones in case the GTFS information is more aggregate than
   * the transfer zones, e.g., if the underlying OSM data has resulted in separate platforms, but the GTFS stop reflects all platforms at once
   * </p>
   *
   * @param gtfsStopId     id of stop location
   * @param transferZoneId Id of waiting area (platform, pole, etc.) (int or long)
   * @param idType         which id of the transfer zone (XML which is in the persisted PLANit file, or external (likely for example the original OSM id)
   */
  public void addOverwriteGtfsStopTransferZoneMapping(final String gtfsStopId, final Object transferZoneId, final IdMapperType idType) {
    var overrides = overwriteGtfsStopTransferZoneExternalIdMapping.get(gtfsStopId);
    if(overrides == null){
      overrides = new ArrayList<>(1);
      overwriteGtfsStopTransferZoneExternalIdMapping.put(gtfsStopId, overrides);
    }
    overrides.add(Pair.of(transferZoneId, idType));
  }

  /**
   * Verify if stop id is marked for overwritten transfer zone mapping
   *
   * @param gtfsStopId to verify
   * @return true when present, false otherwise
   */
  public boolean isOverwrittenGtfsStopTransferZoneMapping(final String gtfsStopId) {
    return overwriteGtfsStopTransferZoneExternalIdMapping.containsKey(gtfsStopId);
  }

  /**
   * get explicitly mapped transfer zone(s') external id for given GTFS stop id  (if any))
   *
   * @param gtfsStopId to collect for
   * @return mapped transfer zone id(s) and the type(s) (null if none is mapped)
   */
  public Collection<Pair<Object, IdMapperType>> getOverwrittenGtfsStopTransferZoneMapping(final String gtfsStopId) {
    return overwriteGtfsStopTransferZoneExternalIdMapping.get(gtfsStopId);
  }

  /**
   * Provide explicit mapping for GTFS stop id to an alternative location. USeful in case the original location is slightly off compared
   * to underlying network making finding an automated mapping to the network problematic. Often, moving the location slghty further away from the road
   * will solve this problem.
   *
   * @param gtfsStopId id of stop location
   * @param latitude   new latitude
   * @param longitude  new longitude
   */
  public void setOverwriteGtfsStopLocation(final String gtfsStopId, double latitude, double longitude) {
    overwriteGtfsStopLocationMapping.put(gtfsStopId, new Coordinate(longitude /*x*/, latitude/*y*/));
  }

  /**
   * Verify if stop id is marked for overwritten location
   *
   * @param gtfsStopId to verify
   * @return true when present, false otherwise
   */
  public boolean isOverwrittenGtfsStopLocationMapping(final String gtfsStopId) {
    return overwriteGtfsStopLocationMapping.containsKey(gtfsStopId);
  }

  /**
   * get explicitly mapped location for given GTFS stop id  (if any))
   *
   * @param gtfsStopId to collect for
   * @return mapped transfer zone (null if none is mapped)
   */
  public Coordinate getOverwrittenGtfsStopLocation(final String gtfsStopId) {
    return overwriteGtfsStopLocationMapping.get(gtfsStopId);
  }

  /**
   * @return true when mapped GTFS stops logged, false otherwise
   */
  public boolean isLogMappedGtfsZones() {
    return logMappedGtfsZones;
  }

  /** Check if extensive logging on how particular GTFS stop is being parsed is activated
   *
   * @param gtfsStopId to verify
   * @return boolean indication if extended logging is activated or not
   */
  public boolean isExtendedLoggingForGtfsZoneActivated(String gtfsStopId){
    return extendedLoggingByGtfsStopId.contains(gtfsStopId);
  }

  /**
   * @param logMappedGtfsZones when true mapped GTFS stops are logged, otherwise not
   */
  public void setLogMappedGtfsZones(boolean logMappedGtfsZones) {
    this.logMappedGtfsZones = logMappedGtfsZones;
  }

  /** Allow user to trigger extensive logging on how particular GTFS stops are being parsed and converted into a PLANit
   * transfer zone
   *
   * @param gtfsStopIds to activate extended logging for
   */
  public void activateExtendedLoggingForGtfsZones(String... gtfsStopIds){
    extendedLoggingByGtfsStopId.addAll(Arrays.asList(gtfsStopIds));
  }

  /**
   * @return true when newly created transfer zones based on GTFS stops logged, false otherwise
   */
  public boolean isLogCreatedGtfsZones() {
    return logCreatedGtfsZones;
  }

  /**
   * @param logCreatedGtfsZones when true, each newly created (unmapped) transfer zones based on GTFS stops are logged, otherwise not
   */
  public void setLogCreatedGtfsZones(boolean logCreatedGtfsZones) {
    this.logCreatedGtfsZones = logCreatedGtfsZones;
  }

  /**
   * @return true when removing unused transfer zones, false otherwise
   */
  public boolean isRemoveUnusedTransferZones() {
    return removeUnusedTransferZones;
  }

  /**
   * @param removeUnusedTransferZones when true, remove unused transfer zones, otherwise do not
   */
  public void setRemoveUnusedTransferZones(boolean removeUnusedTransferZones) {
    this.removeUnusedTransferZones = removeUnusedTransferZones;
  }

  /**
   * {@inheritDoc}
   */
  public void logSettings() {
    LOGGER.info("GTFS zoning reader settings:");
    LOGGER.info(String.format("GTFS stop-to-transfer zone mappings are %slogged", isLogMappedGtfsZones() ? "" : "not "));
    LOGGER.info(String.format("GTFS stop-to-transfer zone search radius (m): %.1f", getGtfsStopToTransferZoneSearchRadiusMeters()));
    LOGGER.info(String.format("GTFS stop-to-link search radius (m): %.1f", getGtfsStopToLinkSearchRadiusMeters()));
    LOGGER.info(String.format("GTFS remove unused transfer zones (stops): %s", isRemoveUnusedTransferZones()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    setGtfsStopToTransferZoneSearchRadiusMeters(DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS);
    setGtfsStopToLinkSearchRadiusMeters(DEFAULT_GTFSSTOP_LINK_SEARCH_METERS);
    setRemoveUnusedTransferZones(DEFAULT_REMOVE_UNUSED_TRANSFER_ZONES);
    excludeGtfsStopsById.clear();
    overwriteGtfsStop2LinkMapping.clear();
    disallowGtfsTop2TransferZoneJointMapping.clear();
    forceCreateNewTransferZoneForGtfsStops.clear();
  }

  /**
   * Provide GTFS stop ids that we are not to parse as public transport infrastructure, for example
   * when we know the stop is problematic and we want to avoid any warnings in our output
   *
   * @param gtfsStopIds to exclude (int or long)
   */
  public void excludeGtfsStopsById(final String... gtfsStopIds) {
    excludeGtfsStopsById(Arrays.asList(gtfsStopIds));
  }

  /**
   * Provide OSM ids of nodes that we are not to parse as public transport infrastructure, for example
   * when we know the stop is problematic and we want to avoid any warnings in our output
   *
   * @param osmIds to exclude (int or long)
   */
  public void excludeGtfsStopsById(final Collection<String> osmIds) {
    osmIds.forEach(osmId -> excludeGtfsStopById(osmId));
  }

  /**
   * Provide id of GTFS stop that we are not to parse as public transport infrastructure, for example
   * when we know the stop is problematic and we want to avoid any warnings in our output
   *
   * @param gtfsStopId to exclude
   */
  public void excludeGtfsStopById(final String gtfsStopId) {
    excludeGtfsStopsById.add(gtfsStopId);
  }

  /**
   * Verify if GTFS stop is marked for exclusion during parsing
   *
   * @param gtfsStopId to verify
   * @return true when excluded false otherwise
   */
  public boolean isExcludedGtfsStop(String gtfsStopId) {
    return excludeGtfsStopsById.contains(gtfsStopId);
  }

  /**
   * Provide explicit mapping for GtfsStop id (platform, bus_stop, pole, station) to a link by one of its id's
   * This forces the parser to use the nominated link as the access link for the GTFS stop.
   * <p>
   * This  is only considered for GTFS stops that could not be matched to existing transfer zones already present in
   * the network since it is assumed existing transfer zones have a correct mapping to the network already
   * </p>
   *
   * @param gtfsStopId   GTFS stop id to provide link mapping for
   * @param linkId       link id to map to
   * @param idMapperType which id of the link to use
   */
  public void overwriteGtfsStopToLinkMapping(final String gtfsStopId, final Object linkId, final IdMapperType idMapperType) {
    overwriteGtfsStop2LinkMapping.put(gtfsStopId, Pair.of(linkId, idMapperType));
  }

  /**
   * Verify if GTFS stop id is marked for overwritten link mapping
   *
   * @param gtfsStopId GTFS stop id to verify
   * @return true when present, false otherwise
   */
  public boolean hasOverwrittenGtfsStopToLinkMapping(final String gtfsStopId) {
    return overwriteGtfsStop2LinkMapping.containsKey(gtfsStopId);
  }

  /**
   * Collect overwritten link id information for GTFS stop id (if present)
   *
   * @param gtfsStopId GTFS stop id to get mapping for
   * @return true when present, false otherwise
   */
  public Pair<Object, IdMapperType> getOverwrittenGtfsStopToLinkMapping(final String gtfsStopId) {
    return overwriteGtfsStop2LinkMapping.get(gtfsStopId);
  }

  /**
   * Log the chosen PLANit link (and its ids) to the user for the given GTFS stop so it can be manually verified what
   * the algorithm has chosen from the logs
   *
   * @param gtfsStopIds to log mapping for
   */
  public void addLogGtfsStopToLinkMapping(final String... gtfsStopIds) {
    logGtfsStop2PlanitLinkMapping.addAll(Arrays.stream(gtfsStopIds).collect(Collectors.toSet()));
  }

  /**
   * Verify if GTFS stop id is marked for logging the link mapping
   *
   * @param gtfsStopId GTFS stop id to verify
   * @return true when present, false otherwise
   */
  public boolean isLogGtfsStopToLinkMapping(final String gtfsStopId) {
    return logGtfsStop2PlanitLinkMapping.contains(gtfsStopId);
  }

  /**
   * Flag that given GTFS stop may not be mapped to a transfer zone together with any other (nearby) GTFS stop. If such a situation
   * is identified, instead a new transfer zone is created instead
   *
   * @param gtfsStopIds GTFS stop id to provide link mapping for
   */
  public void disallowGtfsStopToTransferZoneJointMapping(final String... gtfsStopIds) {
    Arrays.stream(gtfsStopIds).forEach( e -> disallowGtfsTop2TransferZoneJointMapping.add(e));
  }


  /** Verify if GTFS stop id is marked for overwritten link mapping
   *
   * @param gtfsStopId GTFS stop id to verify
   * @return true when present, false otherwise
   */
  public boolean isDisallowGtfsStopToTransferZoneJointMapping(final String gtfsStopId) {
    return disallowGtfsTop2TransferZoneJointMapping.contains(gtfsStopId);
  }

  /**
   * Flag that given GTFS stop must trigger creation of a new transfer zone, so do not attempt to map it to any
   * existing transfer zones.
   *
   * @param gtfsStopIds GTFS stop id to provide link mapping for
   */
  public void forceCreateNewTransferZoneForGtfsStops(final String... gtfsStopIds) {
    Arrays.stream(gtfsStopIds).forEach( e -> forceCreateNewTransferZoneForGtfsStops.add(e));
  }


  /** Verify if GTFS stop id is marked for overwritten link mapping
   *
   * @param gtfsStopId GTFS stop id to verify
   * @return true when present, false otherwise
   */
  public boolean isForceCreateNewTransferZoneForGtfsStops(final String gtfsStopId) {
    return forceCreateNewTransferZoneForGtfsStops.contains(gtfsStopId);
  }

}
