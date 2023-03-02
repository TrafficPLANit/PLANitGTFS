package org.goplanit.gtfs.converter;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.misc.Pair;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Map;

/**
 * Capture all common user configurable settings regarding GTFS converter readers for raw (static) GTFS feeds. To be
 * used as base class not as actual settings class to be exposed.
 * 
 * @author markr
 *
 */
public interface GtfsConverterReaderSettings extends ConverterReaderSettings {

  /** Get the reference network to use
   * 
   * @return referenceNetwork
   */
  public abstract MacroscopicNetwork getReferenceNetwork();

  /** The country name used to initialise GTFS defaults for
   *
   * @return country name
   */
  public abstract String getCountryName();

  /**
   * Collect the input dir to use
   * @return input directory
   */
  public abstract String getInputDirectory();

  /**
   * Log settings used
   */
  public abstract void logSettings();
}
