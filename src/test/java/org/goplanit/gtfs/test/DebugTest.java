package org.goplanit.gtfs.test;

import org.goplanit.converter.idmapping.IdMapperType;
import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderFactory;
import org.goplanit.gtfs.converter.service.GtfsServicesReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderFactory;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.gtfs.scheme.GtfsRoutesScheme;
import org.goplanit.gtfs.test.utils.SydneyGtfsServicesSettingsUtils;
import org.goplanit.gtfs.test.utils.SydneyGtfsZoningSettingsUtils;
import org.goplanit.gtfs.util.GtfsFileConditions;
import org.goplanit.gtfs.util.GtfsUtils;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReader;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderSettings;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Quadruple;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.resource.ResourceUtils;
import org.goplanit.zoning.Zoning;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.goplanit.utils.mode.PredefinedModeType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for Gtfs's API basic functionality
 * 
 * @author markr
 *
 */
public class DebugTest {

  private static Logger LOGGER;

  public static final String GTFS_SEQ_ALL = Path.of("GTFS","SEQ","SEQGTFS.zip").toString();

  private static final Path RESOURCES_GTFS = Path.of("src","test","resources","GTFS");

  public static final Path GTFS_NSW_NO_SHAPES = Path.of("GTFS","NSW","greatersydneygtfsstaticnoshapes.zip");
  //public static final String GTFS_NSW_NO_SHAPES = Path.of(RESOURCES_GTFS.toString(),"NSW","greatersydneygtfsstaticnoshapes.zip").toString();

  public static MacroscopicNetwork macroscopicNetwork;

  public static Zoning zoning;

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(DebugTest.class);
    }
    LOGGER.setLevel(Level.INFO);
  }

  @AfterAll
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
    IdGenerator.reset();
  }

  @BeforeEach
  public void before() throws PlanItException {
  }

  @AfterEach
  public void after() {
    IdGenerator.reset();
  }

  /**
   * Test that attempts to extract PLANit routed services from GTFS data (no filtering based on underlying networks/zoning,
   * just collate all data for a given reference day
   */
  @Test
  public void testGtfsZipAccess() {

    try {
      //String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();
      String GTFS_FILES_DIR = UrlUtils.asLocalPath(UrlUtils.createFromLocalPathOrResource(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

//      var result = GtfsUtils.createInputStream(
//          UrlUtils.createFromLocalPathOrResource(GTFS_NSW_NO_SHAPES), new GtfsRoutesScheme(), GtfsFileConditions.required());

      var result = GtfsUtils.createInputStream(
          UrlUtils.createFromLocalPathOrResource(GTFS_FILES_DIR), new GtfsRoutesScheme(), GtfsFileConditions.required(), true);

      result.close();

    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail("testGtfsZipAccess");
    }

    System.gc();
  }
}
