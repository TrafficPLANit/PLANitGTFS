package org.goplanit.gtfs.converter;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;

import java.util.logging.Logger;

/**
 * Capture all common user configurable settings regarding GTFS converter readers for raw (static) GTFS feeds
 * 
 * @author markr
 *
 */
public class GtfsConverterReaderSettings implements ConverterReaderSettings {

  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(GtfsConverterReaderSettings.class.getCanonicalName());

  /** the reference network to use during parsing of the pt zones */
  private MacroscopicNetwork referenceNetwork = null;

  /** Input directory to use */
  private String inputDirectory;

  /** Country name to use to initialise OSM defaults for */
  private final String countryName;

  /** Constructor with user defined source locale
   * @param countryName to base source locale on
   */
  public GtfsConverterReaderSettings(String countryName) {
    this(null, countryName,null);
  }

  /** Constructor with user defined source locale
   *
   * @param inputDirectory to extract GTFS information from
   * @param countryName to base source locale on
   */
  public GtfsConverterReaderSettings(String inputDirectory, String countryName) {
    this(inputDirectory, countryName, null);
  }

  /** Constructor with user defined source locale
   * @param countryName to base source locale on
   * @param referenceNetwork to use
   */
  public GtfsConverterReaderSettings(String countryName, MacroscopicNetwork referenceNetwork) {
    this(null, countryName, referenceNetwork);
  }

  /** Constructor with user defined source locale
   *
   * @param inputDirectory to use
   * @param countryName to base source locale on
   * @param referenceNetwork to use
   */
  public GtfsConverterReaderSettings(String inputDirectory, String countryName, MacroscopicNetwork referenceNetwork) {
    this.inputDirectory = inputDirectory;
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
   * Set the input dir to use
   *
   * @param inputDirectory to use
   */
  public final void setInputDirectory(String inputDirectory){
    this.inputDirectory = inputDirectory;
  }

  /**
   * Collect the input dir to use
   * @return input directory
   */
  public final String getInputDirectory(){
    return this.inputDirectory;
  }

  /**
   * Log settings used
   */
  public void log() {
    LOGGER.info(String.format("GTFS input source: %s", getInputDirectory()));
    LOGGER.info(String.format("Country: %s", getCountryName()));
  }
}
