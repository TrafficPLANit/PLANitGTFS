package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.cost.physical.PhysicalCost;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.LogCollator;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.UrlUtils;

import java.net.URL;
import java.time.DayOfWeek;
import java.util.logging.Logger;

/**
 * Settings of GtfsIntermodalReader
 * 
 * @author markr
 *
 */
public class GtfsIntermodalReaderSettings implements ConverterReaderSettings {

  private static final Logger LOGGER = Logger.getLogger(GtfsIntermodalReaderSettings.class.getCanonicalName());

  /** default search for cheapest paths is based on free flow approach */
  public final String DEFAULT_STOP_TO_STOP_COST_APPROACH = PhysicalCost.FREEFLOW;

  /** the services settings to use */
  protected final GtfsServicesReaderSettings servicesReaderSettings;
  
  /** the zoning settings to use */
  protected final GtfsZoningReaderSettings zoningSettings;

  private final String stopToStopPathSearchPhysicalCostApproach = DEFAULT_STOP_TO_STOP_COST_APPROACH;

  /** Constructor with user defined source locale, input source the current directory, and
   * EXTENDED RouteTypeChoice applied.
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
  public GtfsIntermodalReaderSettings(
          String inputSource, String countryName, DayOfWeek dayOfWeek, RouteTypeChoice routeTypeChoice) {
    this(inputSource==null ? null : UrlUtils.createFrom(inputSource),
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
  public GtfsIntermodalReaderSettings(
          URL inputSource, String countryName, DayOfWeek dayOfWeek, RouteTypeChoice routeTypeChoice) {
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
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("GTFS Intermodal Reader Settings"));
    getServiceSettings().logSettings(level+1);
    getZoningSettings().logSettings(level+1);
  }

  /** Verify whether the per entity detail behind the logged summary is written to disk
   *
   * @return true when persisted, false otherwise
   */
  public boolean isPersistParseDiagnostics() {
    return getServiceSettings().isPersistParseDiagnostics();
  }

  /** Set whether to write the per entity detail behind the logged summary to disk
   *
   * @param persistParseDiagnostics to set
   */
  public void setPersistParseDiagnostics(boolean persistParseDiagnostics) {
    getServiceSettings().setPersistParseDiagnostics(persistParseDiagnostics);
    getZoningSettings().setPersistParseDiagnostics(persistParseDiagnostics);
  }

  /** The directory the parse diagnostics are written to
   *
   * @return output directory
   */
  public String getParseDiagnosticsOutputDirectory() {
    return getServiceSettings().getParseDiagnosticsOutputDirectory();
  }

  /** Set the directory the parse diagnostics are written to
   *
   * @param parseDiagnosticsOutputDirectory to use
   */
  public void setParseDiagnosticsOutputDirectory(String parseDiagnosticsOutputDirectory) {
    getServiceSettings().setParseDiagnosticsOutputDirectory(parseDiagnosticsOutputDirectory);
    getZoningSettings().setParseDiagnosticsOutputDirectory(parseDiagnosticsOutputDirectory);
  }

  /** How many occurrences of each issue are kept, bounding what an issue affecting millions of entities costs in
   * memory while still allowing a feed to be examined in full when that is what is wanted
   *
   * @return retention limit
   */
  public int getDiagnosticsRetentionLimit() {
    return getServiceSettings().getDiagnosticsRetentionLimit();
  }

  /** Set how many occurrences of each issue are kept
   *
   * @param diagnosticsRetentionLimit to use, {@link LogCollator#UNLIMITED_RETENTION} to keep every occurrence
   */
  public void setDiagnosticsRetentionLimit(int diagnosticsRetentionLimit) {
    getServiceSettings().setDiagnosticsRetentionLimit(diagnosticsRetentionLimit);
    getZoningSettings().setDiagnosticsRetentionLimit(diagnosticsRetentionLimit);
  }

  /** How many entity ids each reported issue lists in the log, the remainder being available in the persisted detail
   *
   * @return sample size
   */
  public int getDiagnosticsSampleSize() {
    return getServiceSettings().getDiagnosticsSampleSize();
  }

  /** Set how many entity ids each reported issue lists in the log
   *
   * @param diagnosticsSampleSize to use
   */
  public void setDiagnosticsSampleSize(int diagnosticsSampleSize) {
    getServiceSettings().setDiagnosticsSampleSize(diagnosticsSampleSize);
    getZoningSettings().setDiagnosticsSampleSize(diagnosticsSampleSize);
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
   *
   * @param inputFile to use
   */
  public void setInputFile(final String inputFile) {
    try{
      var urlInputSource = UrlUtils.createFromLocalAbsoluteOrRelativePath(inputFile);
      getServiceSettings().setInputSource(urlInputSource);
      getZoningSettings().setInputSource(urlInputSource);
    }catch(Exception e) {
      throw new PlanItRunTimeException("Unable to extract URL from input file location %s",inputFile);
    }
  }

  /** The methodology used to find the paths between stops by means of its full canonical class name which is
   * assumed to be supported by PLANit as a valid cost generating method.
   *
   * @return stopToStopPathSearchPhysicalCostApproach*/
  public String getStopToStopPathSearchPhysicalCostApproach() {
    return stopToStopPathSearchPhysicalCostApproach;
  }

}
