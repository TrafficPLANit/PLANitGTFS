package org.goplanit.gtfs.converter.zoning;

import org.goplanit.gtfs.converter.GtfsConverterReaderSettings;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettingsWithModeMapping;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.service.ServiceNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Capture all the user configurable settings regarding how to
 * parse (if at all) (public transport) transfer infrastructure such as stations, poles, platforms, and other
 * stop and transfer related infrastructure captured from raw (static) GTFS feeds
 * 
 * @author markr
 *
 */
public class GtfsZoningReaderSettings extends GtfsConverterReaderSettingsWithModeMapping implements GtfsConverterReaderSettings {
  
  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningReaderSettings.class.getCanonicalName());

  // Optional configuration settings

  /** search radius used when mapping GTFS stops to PLANit transfer zones */
  private double gtfsStop2TransferZoneSearchRadiusMeters = DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS;

  /** search radius used when mapping GTFS stops to PLANit road network, which given that GTFS stop is the vehicle stop location, should be less than distance to pole */
  private double gtfsStop2RoadSearchRadiusMeters = DEFAULT_GTFSSTOP_LINK_SEARCH_METERS;

  /**
   * Provide explicit mapping from GTFS stop (by GTFS stop id) to and existing PLANit transfer zone based on its external id (third party source ide.g., OSM id),
   * This overrides the parser's mapping functionality and maps the GTFS stop to this entity without further checking.
   */
  private final Map<String, String> overwriteGtfsStopTransferZoneExternalIdMapping = new HashMap<>();

  /** flag to indicate if transfer zones that have no services stopping after parsing is complete, are to be removed or not */
  private boolean removeUnusedTransferZones = DEFAULT_REMOVE_UNUSED_TRANSFER_ZONES;

  /** flag indicating if parser should log all GTFS zones that are mapped to existing PLANit  transfer zones */
  private boolean logMappedGtfsZones = DEFAULT_LOG_MAPPED_GTFS_ZONES;

  /** flag indicating if parser should log all GTFS stops that triggered the creation of a new PLANit  transfer zone */
  private boolean logCreatedGtfsZones = DEFAULT_LOG_CREATED_GTFS_ZONES;

  /** default flag setting whether to remove unused transfer zones after GTFS parsing is complete */
  public static final boolean DEFAULT_REMOVE_UNUSED_TRANSFER_ZONES = true;

  /** default search radius for mapping GTFS stops to PLANit transfer zones */
  public static final double DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS = 40.0;

  /** default search radius for mapping GTFS stops to PLANit links */
  public static final double DEFAULT_GTFSSTOP_LINK_SEARCH_METERS = DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS/2.0;

  /** default setting for logging mappings between GTFS zones and existing PLANit transfer zones */
  public static final boolean DEFAULT_LOG_MAPPED_GTFS_ZONES = false;

  /** default setting for logging the creation of new transfer zones based and GTFS stops */
  public static final boolean DEFAULT_LOG_CREATED_GTFS_ZONES = false;


  /**
   * The default buffer distance when looking for links within a distance of the closest link to a GTFS stop to create connectoids (stop_locations).
   * In case candidates are so close just selecting the closest can lead to problems. By identifying multiple candidates via this buffer, we can then use more sophisticated ways than proximity
   * to determine the best candidate
   */
  public static double DEFAULT_CLOSEST_LINK_SEARCH_BUFFER_DISTANCE_M = 5;

  /** Copy constructor creating a shallow copy of the underlying mode mapping so it is synced with the provided settings. Useful when both settings are used
   *  in conjunction and we want to avoid having to sync information
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
  public double getGtfsStopToTransferZoneSearchRadiusMeters(){
    return this.gtfsStop2TransferZoneSearchRadiusMeters;
  }

  /**
   * Search radius in meters to map a GTFS stop location to an existing PLANit transfer zone
   * @param searchRadiusMeters to apply
   */
  public void setGtfsStopToTransferZoneSearchRadiusMeters(double searchRadiusMeters){
    this.gtfsStop2TransferZoneSearchRadiusMeters = searchRadiusMeters;
  }

  /**
   * Search radius in meters to map a GTFS stop location to an existing PLANit link (for its stop location)
   *
   * @return searchRadiusMeters being applied
   */
  public double getGtfsStopToLinkSearchRadiusMeters(){
    return this.gtfsStop2RoadSearchRadiusMeters;
  }

  /**
   * Search radius in meters to map a GTFS stop location to an existing PLANit link (for its stop location)
   * @param searchRadiusMeters to apply
   */
  public void setGtfsStopToLinkSearchRadiusMeters(double searchRadiusMeters){
    this.gtfsStop2RoadSearchRadiusMeters = searchRadiusMeters;
  }


  /**
   * Provide the mapping from a PLANit service node in the service network to its GTFS STOP ID
   *
   * @return function mapping to perform conversion from a given service node to GTFS STOP ID
   */
  public Function<ServiceNode, String> getServiceNodeToGtfsStopIdFunction(){
    return GtfsServicesReaderSettings.getServiceNodeToGtfsStopIdMapping();
  }

  /**
   * Provide explicit mapping for GTFS stop id to an existing PLANit transfer zone, e.g., platform, pole, station, halt, stop, etc. (by its external id, e.g. OSM id)
   * This overrides the parser's mapping functionality and immediately maps the stop to this entity. Can be useful to avoid warnings or wrong mapping of
   * stop locations in case the automated behaviour does not perform as expected.
   *
   * @param gtfsStopId id of stop location
   * @param transferZoneExternalId osm id of waiting area (platform, pole, etc.) (int or long)
   */
  public void setOverwriteGtfsStopTransferZoneMapping(final String gtfsStopId, final String transferZoneExternalId) {
    overwriteGtfsStopTransferZoneExternalIdMapping.put(gtfsStopId,transferZoneExternalId);
  }

  /** Verify if stop id is marked for overwritten transfer zone mapping
   *
   * @param gtfsStopId to verify
   * @return true when present, false otherwise
   */
  public boolean isOverwrittenGtfsStopTransferZoneMapping(final String gtfsStopId) {
    return overwriteGtfsStopTransferZoneExternalIdMapping.containsKey(gtfsStopId);
  }

  /** get explicitly mapped transfer zone's external id for given GTFS stop id  (if any))
   *
   * @param gtfsStopId to collect for
   * @return mapped transfer zone (null if none is mapped)
   */
  public String getOverwrittenGtfsStopTransferZoneMapping(final String gtfsStopId) {
    return overwriteGtfsStopTransferZoneExternalIdMapping.get(gtfsStopId);
  }

  /**
   * @return true when mapped GTFS stops logged, false otherwise
   */
  public boolean isLogMappedGtfsZones() {
    return logMappedGtfsZones;
  }

  /**
   * @param  logMappedGtfsZones when true mapped GTFS stops are logged, otherwise not
   */
  public void setLogMappedGtfsZones(boolean logMappedGtfsZones) {
    this.logMappedGtfsZones = logMappedGtfsZones;
  }

  /**
   * @return true when newly created transfer zones based on GTFS stops logged, false otherwise
   */
  public boolean isLogCreatedGtfsZones() {
    return logCreatedGtfsZones;
  }

  /**
   * @param  logCreatedGtfsZones when true, each newly created (unmapped) transfer zones based on GTFS stops are logged, otherwise not
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
   * @param  removeUnusedTransferZones when true, remove unused transfer zones, otherwise do not
   */
  public void setRemoveUnusedTransferZones(boolean removeUnusedTransferZones) {
    this.removeUnusedTransferZones = removeUnusedTransferZones;
  }

  /**
   * {@inheritDoc}
   */
  public void logSettings() {
    LOGGER.info("GTFS zoning reader settings:");
    LOGGER.info(String.format("GTFS stop-to-transfer zone mappings are %slogged",isLogMappedGtfsZones() ? "" : "not "));
    LOGGER.info(String.format("GTFS stop-to-transfer zone search radius (m): %.1f",getGtfsStopToTransferZoneSearchRadiusMeters()));
    LOGGER.info(String.format("GTFS stop-to-link search radius (m): %.1f",getGtfsStopToLinkSearchRadiusMeters()));
    LOGGER.info(String.format("GTFS remove unused transfer zones (stops): %s",isRemoveUnusedTransferZones()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    setGtfsStopToTransferZoneSearchRadiusMeters(DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS);
    setGtfsStopToLinkSearchRadiusMeters(DEFAULT_GTFSSTOP_LINK_SEARCH_METERS);
    setRemoveUnusedTransferZones(DEFAULT_REMOVE_UNUSED_TRANSFER_ZONES);
  }

}
