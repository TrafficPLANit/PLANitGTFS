package org.goplanit.gtfs.converter.service;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;

/**
 * Configurable settings for the Gtfs to PLANit routed services reader
 *
 * @author markr
 *
 */
public class GtfsServicesReaderSettings extends GtfsConverterReaderSettings implements ConverterReaderSettings {

   /** Constructor with user defined source locale
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param parentNetwork to use
   */
  public GtfsServicesReaderSettings(String inputSource, String countryName,final MacroscopicNetwork parentNetwork) {
    super(inputSource, countryName, parentNetwork);
  }

  /**
   * Constructor where input source and locale are to be provided by user later
   *
   * @param parentNetwork to use
   */
  public GtfsServicesReaderSettings(final MacroscopicNetwork parentNetwork) {
    this(null, null, parentNetwork);
  }

}
