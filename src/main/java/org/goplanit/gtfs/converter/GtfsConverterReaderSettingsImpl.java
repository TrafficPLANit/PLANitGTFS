package org.goplanit.gtfs.converter;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.time.LocalTimeUtils;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

  /** Input directory to use */
  private String inputDirectory;

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
   * @param inputDirectory to use
   * @param countryName to base source locale on
   */
  public GtfsConverterReaderSettingsImpl(String inputDirectory, String countryName) {
    this.inputDirectory = inputDirectory;
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
   * @param inputDirectory to use
   */
  public final void setInputDirectory(String inputDirectory){
    this.inputDirectory = inputDirectory;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final String getInputDirectory(){
    return this.inputDirectory;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings() {
    LOGGER.info(String.format("GTFS input source: %s", getInputDirectory()));
    LOGGER.info(String.format("Country: %s", getCountryName()));
  }

}
