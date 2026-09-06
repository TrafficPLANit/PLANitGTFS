package org.goplanit.gtfs.test;

import org.goplanit.geoio.converter.intermodal.GeometryIntermodalWriter;
import org.goplanit.geoio.converter.intermodal.GeometryIntermodalWriterFactory;
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
import org.locationtech.jts.geom.Envelope;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit test cases for converting networks from one format to another. The PLANit reference network and zoning
 * used here should be in sync with the result produced in the PLANitOSM Melbourne test named:
 * osm_intermodal_no_services_bb. This way when anything material changes in how we parse OSM we can update this
 * network and zoning via that repo.
 * 
 * @author markr
 *
 */
public class Gtfs2PlanitMelbourneTest {

  public static final Path RESOURCE_PATH = Path.of("src", "test", "resources");

  public static final Path GTFS_VIC_NO_SHAPES =
      Path.of(RESOURCE_PATH.toString(), "GTFS", "VIC", "melbourne_gtfs_9_3_2023_no_shapes.zip");

  /** bounding area to apply */
  public static final Envelope MELBOURNE_SIMPLE_BOUNDING_BOX =
      new Envelope(144.995842, 144.921341, -37.855068,-37.786996);

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
   * Test that attempts to extract PLANit routed services, and service network from GTFS data to supplement an
   * existing PLANit network and zoning (stops) read from disk and then persist the result in the PLANit data format.
   */
  @Test
  public void test2Gtfs2PlanitIntermodalWithServices_6_10AM_THU() {

    final String PLANIT_INPUT_DIR = Path.of(
        RESOURCE_PATH.toString(), "planit","melbourne").toAbsolutePath().toString();
    final String GTFS_FILES_INPUT_DIR = GTFS_VIC_NO_SHAPES.toAbsolutePath().toString();
    final String OUTPUT_DIR = Path.of(
        RESOURCE_PATH.toString(),"testcases","melbourne").toAbsolutePath().toString();
    final String PLANIT_REF_DIR = Path.of(
        RESOURCE_PATH.toString(),"planit","melbourne","reference").toAbsolutePath().toString();

    try {

      /* parse PLANit intermodal network (without services) from disk to memory */
      PlanitIntermodalReader planitReader = PlanitIntermodalReaderFactory.create(
          new PlanitIntermodalReaderSettings(PLANIT_INPUT_DIR));
      var planitIntermodalNetworkTuple = planitReader.read();
      var planitNetwork = planitIntermodalNetworkTuple.first();
      var planitZoning = planitIntermodalNetworkTuple.second();

      var inputSettings = new GtfsIntermodalReaderSettings(
              GTFS_FILES_INPUT_DIR,  CountryNames.AUSTRALIA, DayOfWeek.THURSDAY, RouteTypeChoice.EXTENDED);

      /* 6-10 in the morning as time period filter */
      inputSettings.getServiceSettings().addTimePeriodFilter(
          LocalTime.of(6,0,0),
          LocalTime.of(9, 59,59));

      MelbourneGtfsServicesSettingsUtils.minimiseVerifiedWarnings2023(inputSettings.getServiceSettings());
      MelbourneGtfsZoningSettingsUtils.minimiseVerifiedWarnings2023(
          inputSettings.getZoningSettings(), true);

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
      Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> result =
          gtfsIntermodalReader.readWithServices();
      var serviceNetwork = result.third();
      var routedServices = result.fourth();

      /* PLANit intermodal writer */
      {
        PlanitIntermodalWriter planitIntermodalWriter = PlanitIntermodalWriterFactory.create();
        planitIntermodalWriter.getSettings().setCountry(gtfsIntermodalReader.getSettings().getCountryName());
        planitIntermodalWriter.getSettings().setOutputDirectory(OUTPUT_DIR);

        /* configure routed service writer */
        planitIntermodalWriter.getSettings().getRoutedServicesSettings().setLogServicesWithoutTrips(true);

        /* persist */
        planitIntermodalWriter.writeWithServices(planitNetwork, planitZoning, serviceNetwork, routedServices);
      }

      /* Geopackage intermodal writer (for inspection only) */
      {
        GeometryIntermodalWriterFactory.create(OUTPUT_DIR, CountryNames.AUSTRALIA).writeWithServices(
                        result.first(),
                        result.second(),
                        result.third(),
                        result.fourth());
      }

      PlanitAssertionUtils.assertNetworkFilesSimilar(OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertZoningFilesSimilar(OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertServiceNetworkFilesSimilar(OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertRoutedServicesFilesSimilar(OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (Exception e) {
      e.printStackTrace();
      fail("test2Gtfs2PlanitIntermodalWithServices_6_10AM_THU");
    }
  }
  
}