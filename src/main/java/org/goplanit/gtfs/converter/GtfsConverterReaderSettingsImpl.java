package org.goplanit.gtfs.converter;

import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.UrlUtils;

import java.net.URL;
import java.util.logging.Logger;

/**
 * Capture all common user configurable settings regarding GTFS converter readers for raw (static) GTFS feeds. To be
 * used as base class not as actual settings class to be exposed.
 * 
 * @author markr
 *
 */
public class GtfsConverterReaderSettingsImpl implements GtfsConverterReaderSettings {

  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(GtfsConverterReaderSettingsImpl.class.getCanonicalName());

  /** Input source to use */
  private URL inputSource;

  /** Country name to use to initialise OSM defaults for */
  private final String countryName;

  /** Constructor with user defined source locale
   * @param countryName to base source locale on
   */
  public GtfsConverterReaderSettingsImpl(String countryName) {
    this(null, countryName);
  }

  /** Constructor with user defined source locale
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   */
  public GtfsConverterReaderSettingsImpl(URL inputSource, String countryName) {
    this.inputSource = inputSource;
    this.countryName = countryName;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    //todo
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final String getCountryName() {
    return this.countryName;
  }

  /**
   * Set the input dir to use
   *
   * @param inputSource to use
   */
  public final void setInputSource(URL inputSource){
    this.inputSource = inputSource;
  }

  /** Set the input source  to use, we attempt to extract a URL from the String directly here
   *
   * @param inputFile to use
   */
  public void setInputFile(final String inputFile) {
    try {
      setInputSource(UrlUtils.createFrom(inputFile));
    }catch (Exception e) {
      throw new PlanItRunTimeException("Unable to extract URL from input source %s",inputSource);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final URL getInputSource(){
    return this.inputSource;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings() {
    LOGGER.info(String.format("GTFS input source: %s", getInputSource()));
    LOGGER.info(String.format("Country: %s", getCountryName()));
  }

}
