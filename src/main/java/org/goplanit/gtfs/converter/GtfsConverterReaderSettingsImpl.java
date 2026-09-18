package org.goplanit.gtfs.converter;

import org.goplanit.converter.utils.ProjectedBoundingAreaHelper;
import org.goplanit.gtfs.converter.diagnostics.GtfsDiagnosticsBase;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.goplanit.utils.misc.LogCollator;
import org.goplanit.utils.misc.LoggingUtils;
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
  private static final Logger LOGGER = Logger.getLogger(GtfsConverterReaderSettingsImpl.class.getCanonicalName());

  /** by default the per entity detail behind the logged summary is written to disk */
  public static final boolean DEFAULT_PERSIST_PARSE_DIAGNOSTICS = true;

  /** by default the parse diagnostics are written to this directory, relative to the working directory */
  public static final String DEFAULT_PARSE_DIAGNOSTICS_OUTPUT_DIRECTORY = "gtfs_diagnostics";

  /** Input source to use */
  private URL inputSource;

  /** Country name to use to initialise OSM defaults for */
  private final String countryName;

  /** set a bounding polygon specific to GTFS parser */
  private Polygon boundingPolygon;

  /** By default we allow ferries to be a fair way outside any bounding polygon and still be included.
   * We do so because often water bodies are not part of a zoning system and would therefore not include connecting
   * ferries. This is generally unwanted behaviour and therefore we automatically include all ferries within
   * the specified distance outside the bounding polygon and still be included. */
  private double maximumDistanceFerryOutsideBoundingPolygonInMeters =
      ProjectedBoundingAreaHelper.DEFAULT_MAX_FERRY_DISTANCE_OUTSIDE_BOUNDING_AREA_M;

  /** whether to write the per entity detail behind the logged summary to disk */
  private boolean persistParseDiagnostics = DEFAULT_PERSIST_PARSE_DIAGNOSTICS;

  /** directory the parse diagnostics are written to */
  private String parseDiagnosticsOutputDirectory = DEFAULT_PARSE_DIAGNOSTICS_OUTPUT_DIRECTORY;

  /** how many occurrences of each issue are kept for reporting */
  private int diagnosticsRetentionLimit = GtfsDiagnosticsBase.DEFAULT_MAX_RETAINED_PER_ISSUE;

  /** how many entity ids each reported issue lists in the log */
  private int diagnosticsSampleSize = LogCollator.DEFAULT_LOG_SAMPLE_SIZE_OF_RETAINED;

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
    this.persistParseDiagnostics = DEFAULT_PERSIST_PARSE_DIAGNOSTICS;
    this.parseDiagnosticsOutputDirectory = DEFAULT_PARSE_DIAGNOSTICS_OUTPUT_DIRECTORY;
    this.diagnosticsRetentionLimit = GtfsDiagnosticsBase.DEFAULT_MAX_RETAINED_PER_ISSUE;
    this.diagnosticsSampleSize = LogCollator.DEFAULT_LOG_SAMPLE_SIZE_OF_RETAINED;
  }

  /**
   * boundary to restrict parsing to
   *
   * @param boundingPolygon to apply
   */
  public void setBoundingArea(final Polygon boundingPolygon){
    this.boundingPolygon = boundingPolygon;
  }

  /**
   * boundary to restrict parsing to
   *
   * @param boundingEnvelope to apply
   */
  public void setBoundingArea(final Envelope boundingEnvelope){
    this.boundingPolygon = PlanitJtsUtils.create2DPolygon(boundingEnvelope);
  }

  /**
   * The boundingPolygon configured by the user
   *
   * @return boundingPolygon
   */
  public Polygon getBoundingArea(){
    return this.boundingPolygon;
  }

  /** Set a polygon based bounding box to restrict parsing to
   *
   * @return boundingPolygon used, can be null
   */
  public final boolean hasBoundingBoundary() {
    return this.boundingPolygon!=null;
  }

  /** Get the maximum distance outside the bounding area PLANit will still include ferry routes
   *
   * @return distance set
   */
  public double getMaximumDistanceFerryOutsideBoundingPolygonInMeters() {
    return maximumDistanceFerryOutsideBoundingPolygonInMeters;
  }

  /** Set the maximum distance outside the bounding area PLANit will still include ferry routes
   *
   * @param distanceMeters to use
   */
  public void setMaximumDistanceFerryOutsideBoundingPolygonInMeters(double distanceMeters) {
    this.maximumDistanceFerryOutsideBoundingPolygonInMeters = distanceMeters;
  }

  /** Verify whether the per entity detail behind the logged summary is written to disk
   *
   * @return true when persisted, false otherwise
   */
  public boolean isPersistParseDiagnostics() {
    return persistParseDiagnostics;
  }

  /** Set whether to write the per entity detail behind the logged summary to disk
   *
   * @param persistParseDiagnostics to set
   */
  public void setPersistParseDiagnostics(boolean persistParseDiagnostics) {
    this.persistParseDiagnostics = persistParseDiagnostics;
  }

  /** The directory the parse diagnostics are written to
   *
   * @return output directory
   */
  public String getParseDiagnosticsOutputDirectory() {
    return parseDiagnosticsOutputDirectory;
  }

  /** Set the directory the parse diagnostics are written to
   *
   * @param parseDiagnosticsOutputDirectory to use
   */
  public void setParseDiagnosticsOutputDirectory(String parseDiagnosticsOutputDirectory) {
    this.parseDiagnosticsOutputDirectory = parseDiagnosticsOutputDirectory;
  }

  /** How many occurrences of each issue are kept, bounding what an issue affecting millions of entities costs in
   * memory while still allowing a feed to be examined in full when that is what is wanted
   *
   * @return retention limit
   */
  public int getDiagnosticsRetentionLimit() {
    return diagnosticsRetentionLimit;
  }

  /** Set how many occurrences of each issue are kept
   *
   * @param diagnosticsRetentionLimit to use, {@link LogCollator#UNLIMITED_RETENTION} to keep every occurrence
   */
  public void setDiagnosticsRetentionLimit(int diagnosticsRetentionLimit) {
    this.diagnosticsRetentionLimit = diagnosticsRetentionLimit;
  }

  /** How many entity ids each reported issue lists in the log, the remainder being available in the persisted detail
   *
   * @return sample size
   */
  public int getDiagnosticsSampleSize() {
    return diagnosticsSampleSize;
  }

  /** Set how many entity ids each reported issue lists in the log
   *
   * @param diagnosticsSampleSize to use
   */
  public void setDiagnosticsSampleSize(int diagnosticsSampleSize) {
    this.diagnosticsSampleSize = diagnosticsSampleSize;
  }

  /**
   * Log how what became of the GTFS entities is to be reported
   *
   * @param level to indent by
   */
  public void logDiagnosticsSettings(int level) {
    LOGGER.info(LoggingUtils.settingsValue(
        "Diagnostics entity id samples per issue", getDiagnosticsSampleSize(), level));
    LOGGER.info(LoggingUtils.settingsValue(
        "Diagnostics occurrences retained per issue",
        getDiagnosticsRetentionLimit() == LogCollator.UNLIMITED_RETENTION
            ? "all" : String.valueOf(getDiagnosticsRetentionLimit()), level));
    if(isPersistParseDiagnostics()){
      /* state where the detail behind the collated log lines went, a path only discoverable from the source being a
       * path nobody finds */
      LOGGER.info(LoggingUtils.settingsValue(
          "Persist parse diagnostics to", getParseDiagnosticsOutputDirectory(), level));
    }else{
      LOGGER.info(LoggingUtils.settingsValue("Persist parse diagnostics", false, level));
    }
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
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsValue("Input source", getInputSource(), level));
    LOGGER.info(LoggingUtils.settingsValue("Country", getCountryName(), level));
    logDiagnosticsSettings(level);
  }

}
