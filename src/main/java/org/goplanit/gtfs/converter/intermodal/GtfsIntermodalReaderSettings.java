package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.cost.physical.PhysicalCost;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.UrlUtils;

import java.net.URL;
import java.time.DayOfWeek;

/**
 * Settings of GtfsIntermodalReader
 * 
 * @author markr
 *
 */
public class GtfsIntermodalReaderSettings implements ConverterReaderSettings {

  /** default search for cheapest paths is based on free flow approach */
  public final String DEFAULT_STOP_TO_STOP_COST_APPROACH = PhysicalCost.FREEFLOW;
  
  /** the services settings to use */
  protected final GtfsServicesReaderSettings servicesReaderSettings;
  
  /** the zoning settings to use */
  protected final GtfsZoningReaderSettings zoningSettings;

  private final String stopToStopPathSearchPhysicalCostApproach = DEFAULT_STOP_TO_STOP_COST_APPROACH;

  /** Constructor with user defined source locale
   *
   * @param countryName to base source locale on
   */
  public GtfsIntermodalReaderSettings(String countryName) {
    this(".", countryName, RouteTypeChoice.EXTENDED);
  }

  /** Constructor with user defined source locale
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param routeTypeChoice to apply
   */
  public GtfsIntermodalReaderSettings(String inputSource, String countryName, RouteTypeChoice routeTypeChoice) {
    this.servicesReaderSettings = new GtfsServicesReaderSettings(inputSource, countryName, routeTypeChoice);
    this.zoningSettings = new GtfsZoningReaderSettings(servicesReaderSettings);
  }

  /** Constructor with user defined source locale
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param dayOfWeek to filter on
   * @param routeTypeChoice to apply
   */
  public GtfsIntermodalReaderSettings(String inputSource, String countryName, DayOfWeek dayOfWeek, RouteTypeChoice routeTypeChoice) {
    this((URL) (inputSource==null ? null : UrlUtils.createFrom(inputSource)),
        countryName,
        dayOfWeek,
        routeTypeChoice);
  }

  /** Constructor with user defined source locale
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param dayOfWeek to filter on
   * @param routeTypeChoice to apply
   */
  public GtfsIntermodalReaderSettings(URL inputSource, String countryName, DayOfWeek dayOfWeek, RouteTypeChoice routeTypeChoice) {
    this.servicesReaderSettings = new GtfsServicesReaderSettings(inputSource, countryName, dayOfWeek, routeTypeChoice);
    this.zoningSettings = new GtfsZoningReaderSettings(servicesReaderSettings);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    getServiceSettings().reset();
    getZoningSettings().reset();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings() {
    getServiceSettings().logSettings();
    getZoningSettings().logSettings();
  }

  /** provide access to the service reader settings
   * @return network reader settings
   */
  public GtfsServicesReaderSettings getServiceSettings() {
    return servicesReaderSettings;
  }
  
  /** provide access to the zoning reader settings
   * @return zoning reader settings
   */
  public GtfsZoningReaderSettings getZoningSettings() {
    return zoningSettings;
  }

  /**
   * {@inheritDoc}
   */
  public String getCountryName() {
    return servicesReaderSettings.getCountryName();
  }

  /**
   * {@inheritDoc}
   */
  public URL getInputSource() {
    return servicesReaderSettings.getInputSource();
  }

  /** Set the input file to use, which is internally converted into a URL
   * @param inputFile to use
   */
  public void setInputFile(final String inputFile) {
    try{
      servicesReaderSettings.setInputSource(UrlUtils.createFromLocalPath(inputFile));
    }catch(Exception e) {
      throw new PlanItRunTimeException("Unable to extract URL from input file location %s",inputFile);
    }
  }

  /** The methodology used to find the paths between stops by means of its full canonical class name which is assumed to be supported by
   * PLANit as a valid cost generating method
   *
   * @return stopToStopPathSearchPhysicalCostApproach*/
  public String getStopToStopPathSearchPhysicalCostApproach() {
    return stopToStopPathSearchPhysicalCostApproach;
  }

}
