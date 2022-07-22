package org.goplanit.gtfs.converter.zoning;

import org.goplanit.gtfs.converter.GtfsConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;

import java.util.logging.Logger;

/**
 * Capture all the user configurable settings regarding how to
 * parse (if at all) (public transport) transfer infrastructure such as stations, poles, platforms, and other
 * stop and transfer related infrastructure captured from raw (static) GTFS feeds
 * 
 * @author markr
 *
 */
public class GtfsZoningReaderSettings extends GtfsConverterReaderSettings {
  
  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(GtfsZoningReaderSettings.class.getCanonicalName());

  // Optional configuration settings

  /** search radius used when mapping GTFS stops to PLANit transfer zones */
  private double gtfsStop2TransferZoneSearchRadiusMeters = DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS;

  /** default search radius for mapping GTFS stops to PLANit transfer zones */
  public static final double DEFAULT_GTFSSTOP_TRANSFERZONE_SEARCH_METERS = 20;

  /** Constructor with user defined source locale 
   * @param countryName to base source locale on
   */
  public GtfsZoningReaderSettings(String countryName) {
    this(null, countryName,null);
  }
  
  /** Constructor with user defined source locale 
   * 
   * @param inputSource to extract GTFS information from
   * @param countryName to base source locale on
   */
  public GtfsZoningReaderSettings(String inputSource, String countryName) {
    this(inputSource, countryName, null);
  }  
  
  /** Constructor with user defined source locale 
   * @param countryName to base source locale on
   * @param referenceNetwork to use
   */
  public GtfsZoningReaderSettings(String countryName, MacroscopicNetwork referenceNetwork) {
    this(null, countryName, referenceNetwork);
  }  
  
  /** Constructor with user defined source locale
   * 
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param referenceNetwork to use
   */
  public GtfsZoningReaderSettings(String inputSource, String countryName, MacroscopicNetwork referenceNetwork) {
    super(inputSource, countryName, referenceNetwork);
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
   * Log settings used
   */
  public void log() {
    super.log();
    LOGGER.info(String.format("GTFS stop-to-transferzone search radius (m): %.1f",getGtfsStopToTransferZoneSearchRadiusMeters()));
  }
}
