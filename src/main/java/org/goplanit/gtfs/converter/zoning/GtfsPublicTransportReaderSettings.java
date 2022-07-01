package org.goplanit.gtfs.converter.zoning;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;

import java.net.URL;
import java.util.logging.Logger;

/**
 * Capture all the user configurable settings regarding how to
 * parse (if at all) (public transport) transfer infrastructure such as stations, poles, platforms, and other
 * stop and transfer related infrastructure captured from raw (static) GTFS feeds
 * 
 * @author markr
 *
 */
public class GtfsPublicTransportReaderSettings implements ConverterReaderSettings {
  
  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(GtfsPublicTransportReaderSettings.class.getCanonicalName());

  /** the reference network to use during parsing of the pt zones */
  private MacroscopicNetwork referenceNetwork = null;

  /** Input source to use */
  private String inputSource;

  /** Country name to use to initialise OSM defaults for */
  private final String countryName;

  /** Constructor with user defined source locale 
   * @param countryName to base source locale on
   */
  public GtfsPublicTransportReaderSettings(String countryName) {
    this(null, countryName,null);
  }
  
  /** Constructor with user defined source locale 
   * 
   * @param inputSource to extract GTFS information from
   * @param countryName to base source locale on
   */
  public GtfsPublicTransportReaderSettings(String inputSource, String countryName) {
    this(inputSource, countryName, null);
  }  
  
  /** Constructor with user defined source locale 
   * @param countryName to base source locale on
   * @param referenceNetwork to use
   */
  public GtfsPublicTransportReaderSettings(String countryName, MacroscopicNetwork referenceNetwork) {
    this(null, countryName, referenceNetwork);
  }  
  
  /** Constructor with user defined source locale
   * 
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param referenceNetwork to use
   */
  public GtfsPublicTransportReaderSettings(String inputSource, String countryName, MacroscopicNetwork referenceNetwork) {
    this.inputSource = inputSource;
    this.countryName = countryName;
    this.referenceNetwork = referenceNetwork;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    //TODO
  }
  
  // TRANSFERRED FROM NETWORK READER
  
  /** Set the reference network to use
   * @param referenceNetwork to use
   */
  public void setReferenceNetwork(MacroscopicNetwork referenceNetwork) {
    this.referenceNetwork = referenceNetwork;
  }  
  
  /** Get the reference network to use
   * 
   * @return referenceNetwork
   */
  public MacroscopicNetwork getReferenceNetwork() {
    return this.referenceNetwork;
  }

  /** The country name used to initialise GTFS defaults for
   *
   * @return country name
   */
  public final String getCountryName() {
    return this.countryName;
  }

  /**
   * Set the input source to use
   *
   * @param inputSource to use
   */
  public final void setInputSource(String inputSource){
    this.inputSource = inputSource;
  }

  /**
   * Collect the input source to use
   * @return input source
   */
  public final String getInputSource(){
    return this.inputSource;
  }


}
