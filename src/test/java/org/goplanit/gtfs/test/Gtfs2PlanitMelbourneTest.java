package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderFactory;
import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderSettings;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.gtfs.util.test.MelbourneGtfsServicesSettingsUtils;
import org.goplanit.gtfs.util.test.MelbourneGtfsZoningSettingsUtils;
import org.goplanit.io.converter.intermodal.*;
import org.goplanit.io.test.PlanitAssertionUtils;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Quadruple;
import org.goplanit.utils.resource.ResourceUtils;
import org.goplanit.zoning.Zoning;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit test cases for converting networks from one format to another
 * 
 * @author markr
 *
 */
public class Gtfs2PlanitMelbourneTest {

  public static final Path RESOURCE_PATH = Path.of("src", "test", "resources");

  public static final Path GTFS_VIC_NO_SHAPES = Path.of("GTFS", "VIC", "melbourne_gtfs_9_3_2023_no_shapes.zip");

  /** the logger */
  private static Logger LOGGER = null;

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(Gtfs2PlanitMelbourneTest.class);
    }
    IdGenerator.reset();
  }

  @AfterEach
  public void afterEach() {
    IdGenerator.reset();
  }

  @AfterAll
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
  }

  /**
   * Test that attempts to extract PLANit routed services, and service network from GTFS data to supplement an existing PLANit network and zoning (stops)
   * read from disk and then persist the result in the PLANit data format.
   */
  @Test
  public void test2Gtfs2PlanitIntermodalWithServices_6_10AM_THU() {

    final String PLANIT_INPUT_DIR = Path.of(RESOURCE_PATH.toString(), "planit","melbourne").toAbsolutePath().toString();
    final String GTFS_FILES_INPUT_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_VIC_NO_SHAPES.toString())).toAbsolutePath().toString();
    final String PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","melbourne").toAbsolutePath().toString();
    final String PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),"planit","melbourne","reference").toAbsolutePath().toString();
    
    try {

      /* parse PLANit intermodal network (without services) from disk to memory */
      PlanitIntermodalReader planitReader = PlanitIntermodalReaderFactory.create(new PlanitIntermodalReaderSettings(PLANIT_INPUT_DIR));
      var planitIntermodalNetworkTuple = planitReader.read();
      var planitNetwork = planitIntermodalNetworkTuple.first();
      var planitZoning = planitIntermodalNetworkTuple.second();

      var inputSettings = new GtfsIntermodalReaderSettings(GTFS_FILES_INPUT_DIR,  CountryNames.AUSTRALIA, DayOfWeek.THURSDAY, RouteTypeChoice.EXTENDED);

      /* 6-10 in the morning as time period filter */
      inputSettings.getServiceSettings().addTimePeriodFilter(
          LocalTime.of(6,0,0),
          LocalTime.of(9, 59,59));

      MelbourneGtfsServicesSettingsUtils.minimiseVerifiedWarnings2023(inputSettings.getServiceSettings());
      MelbourneGtfsZoningSettingsUtils.minimiseVerifiedWarnings2023(inputSettings.getZoningSettings(), true);

      /* debugging option examples*/
      {
//        // EXAMPLE:
//        inputSettings.getServiceSettings().excludeAllGtfsRoutesExceptByShortName("902");
//        inputSettings.getZoningSettings().activateExtendedLoggingForGtfsZones("925","926");
//
//        gtfsIntermodalReader.getSettings().getZoningSettings().setLogMappedGtfsZones(true);
//        gtfsIntermodalReader.getSettings().getZoningSettings().setLogCreatedGtfsZones(true);
//
//        gtfsIntermodalReader.getSettings().getServiceSettings().activateLoggingForGtfsRouteByShortName("607X");
      }

      /* the GTFS reader */
      var gtfsIntermodalReader = GtfsIntermodalReaderFactory.create(planitNetwork, planitZoning, inputSettings);

      /* execute */
      Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> result = gtfsIntermodalReader.readWithServices();
      var serviceNetwork = result.third();
      var routedServices = result.fourth();

      /* PLANit intermodal writer */
      PlanitIntermodalWriter planitIntermodalWriter = PlanitIntermodalWriterFactory.create();
      planitIntermodalWriter.getSettings().setCountry(gtfsIntermodalReader.getSettings().getCountryName());
      planitIntermodalWriter.getSettings().setOutputDirectory(PLANIT_OUTPUT_DIR);

      /* configure routed service writer */
      planitIntermodalWriter.getSettings().getRoutedServicesSettings().setLogServicesWithoutTrips(true);

      /* persist */
      planitIntermodalWriter.writeWithServices(planitNetwork, planitZoning, serviceNetwork, routedServices);

      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertServiceNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertRoutedServicesFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (Exception e) {
      e.printStackTrace();
      fail("test2Gtfs2PlanitIntermodalWithServices_6_10AM_THU");
    }
  }
  
}