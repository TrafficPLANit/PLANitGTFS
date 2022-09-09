package org.goplanit.gtfs.converter.zoning;

import org.goplanit.gtfs.converter.GtfsConverterReaderSettings;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.service.ServiceNode;

import java.util.Collection;
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
public class GtfsZoningReaderSettings implements GtfsConverterReaderSettings {
  
  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningReaderSettings.class.getCanonicalName());

  // Optional configuration settings

  /** search radius used when mapping GTFS stops to PLANit transfer zones */
  private double gtfsStop2TransferZoneSearchRadiusMeters = DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS;

  /** default search radius for mapping GTFS stops to PLANit transfer zones */
  public static final double DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS = 40.0;

  /** search radius used when mapping GTFS stops to PLANit road network, which given that GTFS stop is the vehicle stop location, should be less than distance to pole */
  private double gtfsStop2RoadSearchRadiusMeters = DEFAULT_GTFSSTOP_LINK_SEARCH_METERS;

  /** default search radius for mapping GTFS stops to PLANit links */
  public static final double DEFAULT_GTFSSTOP_LINK_SEARCH_METERS = DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS/2.0;

  /** re-use settings from services reader */
  private final GtfsServicesReaderSettings servicesReaderSettings;

  /** Constructor leveraging the services reader settings as base information
   *
   * @param servicesReaderSettings to obtain mode mapping information from
   */
  public GtfsZoningReaderSettings(GtfsServicesReaderSettings servicesReaderSettings) {
    this.servicesReaderSettings = servicesReaderSettings;
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
   * @return function mapping to perforsm conversion from a given service node to GTFS STOP ID
   */
  public Function<ServiceNode, String> getServiceNodeToGtfsStopIdFunction(){
    return GtfsServicesReaderSettings.getServiceNodeToGtfsStopIdMapping();
  }

  /** Convenience method that collects the currently mapped PLANit mode for the given GTFS mode if any
   *
   * @param gtfsMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public Mode getPlanitModeIfActivated(final RouteType gtfsMode) {
    return servicesReaderSettings.getPlanitModeIfActivated(gtfsMode);
  }

  /** Convenience method that provides access to all the currently active GTFS modes (unmodifiable)
   *
   * @return mapped GTFS modes, if not available (due to lack of mapping or inactive parser) empty collection is returned
   */
  public Collection<RouteType> getAcivatedGtfsModes() {
    return servicesReaderSettings.getAcivatedGtfsModes();
  }

  /** Convenience method that collects all the currently mapped GTFS modes for the given PLANit mode
   *
   * @param planitMode to collect mapped mode for (if any)
   * @return mapped GTFS modes, if not available (due to lack of mapping or inactive parser) empty collection is returned
   */
  public Collection<RouteType> getAcivatedGtfsModes(final Mode planitMode) {
    return servicesReaderSettings.getAcivatedGtfsModes(planitMode);
  }

  /**
   * Currently activated mapped PLANit modes
   * @return activated, i.e., mapped PLANit modes
   */
  public Set<Mode> getAcivatedPlanitModes() {
    return servicesReaderSettings.getAcivatedPlanitModes();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MacroscopicNetwork getReferenceNetwork() {
    return servicesReaderSettings.getReferenceNetwork();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getCountryName() {
    return servicesReaderSettings.getCountryName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getInputDirectory() {
    return servicesReaderSettings.getInputDirectory();
  }

  /**
   * {@inheritDoc}
   */
  public void log() {
    LOGGER.info(String.format("GTFS stop-to-transfer zone search radius (m): %.1f",getGtfsStopToTransferZoneSearchRadiusMeters()));
    LOGGER.info(String.format("GTFS stop-to-link search radius (m): %.1f",getGtfsStopToLinkSearchRadiusMeters()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    setGtfsStopToTransferZoneSearchRadiusMeters(DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS);
    setGtfsStopToLinkSearchRadiusMeters(DEFAULT_GTFSSTOP_LINK_SEARCH_METERS);
  }
}
