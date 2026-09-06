package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderFactory;
import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderSettings;
import org.goplanit.gtfs.converter.service.GtfsServicesReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderFactory;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.gtfs.util.test.SydneyGtfsServicesSettingsUtils;
import org.goplanit.gtfs.util.test.SydneyGtfsZoningSettingsUtils;
import org.goplanit.io.converter.intermodal.*;
import org.goplanit.io.test.PlanitAssertionUtils;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Quadruple;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.mode.Modes;
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
 * Unit tests for Gtfs's API basic functionality. PLANit reference network and zoning are expected to be synced with
 * the results produced in the PLANitOSM
 * repo (Sydney: src/test/resources/planit/sydney/osm_intermodal_no_services_access_egress_attach, so they can easily be
 * updated
 * 
 * @author markr
 *
 */
public class GtfsToPlanitSydneyTest {

  private static Logger LOGGER;

  public static final Path RESOURCE_PATH = Path.of("src", "test", "resources");

  public static final Path GTFS_NSW_NO_SHAPES = Path.of(
      RESOURCE_PATH.toString(), "GTFS","NSW","greatersydneygtfsstaticnoshapes.zip");

  private static final String PLANIT_SYDNEY_INTERMODAL_DIR = Path.of(
      RESOURCE_PATH.toString(), "planit","sydney").toString();

  final String PLANIT_REF_DIR = Path.of(
      PLANIT_SYDNEY_INTERMODAL_DIR,"reference").toAbsolutePath().toString();


  public static MacroscopicNetwork macroscopicNetwork;

  public static Zoning zoning;

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(GtfsToPlanitSydneyTest.class);
    }

    LOGGER.setLevel(Level.SEVERE);

    /* parse PLANit intermodal network from disk to memory */
    PlanitIntermodalReader planitReader =
        PlanitIntermodalReaderFactory.create(new PlanitIntermodalReaderSettings(PLANIT_SYDNEY_INTERMODAL_DIR));
    var planitIntermodalNetworkTuple = planitReader.read();
    macroscopicNetwork = planitIntermodalNetworkTuple.first();
    zoning = planitIntermodalNetworkTuple.second();
    LOGGER.setLevel(Level.INFO);
  }

  @AfterAll
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
    IdGenerator.reset();
  }

  @AfterEach
  public void after() {
    IdGenerator.reset();
  }

  /**
   * Test that attempts to extract PLANit routed services from GTFS data (no filtering based on
   * underlying networks/zoning, just collate all data for a given reference day
   */
  @Test
  public void testGtfsRoutedServicesReader() {

    try {
      //String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();
      String GTFS_FILES_DIR = UrlUtils.asLocalPath(
          UrlUtils.createFromLocalPathOrResource(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

      var networkCopy = macroscopicNetwork.deepClone();
      GtfsServicesReader servicesReader = GtfsServicesReaderFactory.create(
          networkCopy, GTFS_FILES_DIR, CountryNames.AUSTRALIA, DayOfWeek.THURSDAY, RouteTypeChoice.EXTENDED);
      Pair<ServiceNetwork,RoutedServices> servicesPair = servicesReader.read();

      var serviceNetwork = servicesPair.first();
      var routedServices = servicesPair.second();
      assertEquals(serviceNetwork.getTransportLayers().size(),1);

      /* service nodes correspond to stops which are situated uniquely depending on the side of the road/track. Hence,
       * for now there is an equal number of legs and leg segments ad no bi-directional entries are identified */

      //todo: it is not manually verified the below numbers are correct, but if this fails, we at least know something has changed in how we process the same underlying data
      // and a conscious choice has to be made whether this is better or not before changing the below results
      assertEquals(serviceNetwork.getTransportLayers().getFirst().getServiceNodes().size(),58273);
      assertEquals(serviceNetwork.getTransportLayers().getFirst().getLegSegments().size(),92381);
      assertEquals(serviceNetwork.getTransportLayers().getFirst().getLegs().size(),92381);

      var serviceLayer = routedServices.getLayers().getFirst();
      assertEquals(routedServices.getLayers().size(),1);
      assertEquals(serviceLayer.getServicesByMode(macroscopicNetwork.getModes().get(BUS)).size(),7903);
      assertEquals(serviceLayer.getServicesByMode(macroscopicNetwork.getModes().get(LIGHTRAIL)).size(),5);
      assertEquals(serviceLayer.getServicesByMode(macroscopicNetwork.getModes().get(TRAIN)).size(),39);
      assertEquals(serviceLayer.getServicesByMode(macroscopicNetwork.getModes().get(SUBWAY)).size(),0);
      assertEquals(serviceLayer.getServicesByMode(macroscopicNetwork.getModes().get(FERRY)).size(),23);

    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail("testGtfsRoutedServicesReader");
    }

    System.gc();
  }

  /**
   * Test that attempts to extract PLANit routed services, service network and zoning from GTFS data given the
   * PLANit network exists, but it has no transfer zones yet,i.e., all transfer zones (stops) are to be based on the
   * GTFS data only.
   */
  @Test
  public void testGtfsIntermodalReaderIgnorePreExistingPlanitTransferZones() {

    try {
      //String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();
      var GTFS_FILES_DIR = GTFS_NSW_NO_SHAPES.toString();

      var copiedNetwork = macroscopicNetwork.deepClone();
      /* construct intermodal reader without pre-existing zoning */
      var gtfsIntermodalReader = GtfsIntermodalReaderFactory.create(
          GTFS_FILES_DIR, CountryNames.AUSTRALIA, DayOfWeek.THURSDAY, copiedNetwork, RouteTypeChoice.EXTENDED);

      /* 6-10 in the morning as time period filter */
      gtfsIntermodalReader.getSettings().getServiceSettings().addTimePeriodFilter(
          LocalTime.of(6,0,0),
          LocalTime.of(9, 59,59));

      // log mappings, useful for debugging if needed
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogMappedGtfsZones(true);
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogCreatedGtfsZones(true);

      SydneyGtfsZoningSettingsUtils.minimiseVerifiedWarnings(
          gtfsIntermodalReader.getSettings().getZoningSettings(), false);
      SydneyGtfsServicesSettingsUtils.minimiseVerifiedWarnings(
          gtfsIntermodalReader.getSettings().getServiceSettings());

      Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> result =
          gtfsIntermodalReader.readWithServices();

      var network = result.first();
      var zoning = result.second();
      var serviceNetwork = result.third();
      var routedServices = result.fourth();

      assertEquals(1, network.getTransportLayers().size());
      assertEquals(1383, network.getTransportLayers().getFirst().getNumberOfLinks());
      assertEquals(1161, network.getTransportLayers().getFirst().getNumberOfNodes());
      assertEquals(2739, network.getTransportLayers().getFirst().getNumberOfLinkSegments());
      assertEquals(60, network.getTransportLayers().getFirst().getNumberOfBannedMovements());

      assertEquals(0, zoning.getOdZones().size());
      assertEquals(101, zoning.getTransferZones().size()); // was 109
      assertEquals(0, zoning.getOdConnectoids().size());
      assertEquals(153, zoning.getTransferConnectoids().size()); // was 177

      assertEquals(serviceNetwork.getTransportLayers().size(),1);
      assertEquals(serviceNetwork.getTransportLayers().getFirst().getServiceNodes().size(),97); // was99

      /* service nodes correspond to stops which are situated uniquely depending on the side of the road/track. Hence,
       * for now there is an equal number of legs and leg segments ad no bi-directional entries are identified */
      assertEquals(serviceNetwork.getTransportLayers().getFirst().getLegSegments().size(),82); // was 84
      assertEquals(serviceNetwork.getTransportLayers().getFirst().getLegs().size(),82); // was 84

      assertEquals(routedServices.getLayers().size(),1);
      Modes modes = macroscopicNetwork.getModes();
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(modes.get(BUS)).size(),53);
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(modes.get(LIGHTRAIL)).size(),2);
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(modes.get(TRAIN)).size(),8);
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(modes.get(SUBWAY)).size(),0);
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(modes.get(FERRY)).size(),5);

    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail("testGtfsIntermodalReaderWithoutPreExistingPlanitTransferZones");
    }

    System.gc();
  }

  /**
   * Test that attempts to extract PLANit routed services, service network and zoning  from GTFS data given the PLANit
   * network already has existing transfer zones present that will be fused/merged when found in GTFS
   */
  @Test
  public void testGtfsIntermodalReaderWithPreExistingPlanitTransferZones() {

    try {
      var GTFS_FILES_DIR = GTFS_NSW_NO_SHAPES.toString();

      /* instead of using existing memory model for network and zoning, we use a PLANit intermodal reader to
      parse instead and let the GTFS reader perform the internal memory model construction */
      var planitReader = PlanitIntermodalReaderFactory.create(
          new PlanitIntermodalReaderSettings(PLANIT_SYDNEY_INTERMODAL_DIR));
      var gtfsSettings =  new GtfsIntermodalReaderSettings(
          GTFS_FILES_DIR, CountryNames.AUSTRALIA, RouteTypeChoice.EXTENDED);

      /* 6-10 in the morning as time period filter */
      gtfsSettings.getServiceSettings().setDayOfWeek(DayOfWeek.THURSDAY);
      gtfsSettings.getServiceSettings().addTimePeriodFilter(
          LocalTime.of(6,0,0),
          LocalTime.of(9, 59,59));

      var gtfsIntermodalReader = GtfsIntermodalReaderFactory.create(gtfsSettings, planitReader);

      /* log mappings, useful for debugging if needed */
//    gtfsIntermodalReader.getSettings().getZoningSettings().setLogMappedGtfsZones(true);
//    gtfsIntermodalReader.getSettings().getZoningSettings().setLogCreatedGtfsZones(true);

      SydneyGtfsZoningSettingsUtils.minimiseVerifiedWarnings(
          gtfsIntermodalReader.getSettings().getZoningSettings(), true);
      SydneyGtfsServicesSettingsUtils.minimiseVerifiedWarnings(
          gtfsIntermodalReader.getSettings().getServiceSettings());

      var result = gtfsIntermodalReader.readWithServices();
      var parsedNetwork = result.first();
      var parsedZoning = result.second();
      var serviceNetwork = result.third();
      var routedServices = result.fourth();

      /* PLANit intermodal writer --> to supply file based outputs if needed (example) */
      final String PLANIT_OUTPUT_DIR =
          Path.of(RESOURCE_PATH.toString(),"testcases","sydney").toAbsolutePath().toString();
      PlanitIntermodalWriter planitIntermodalWriter = PlanitIntermodalWriterFactory.create();
      planitIntermodalWriter.getSettings().setCountry(gtfsIntermodalReader.getSettings().getCountryName());
      planitIntermodalWriter.getSettings().setOutputDirectory(PLANIT_OUTPUT_DIR);
      planitIntermodalWriter.writeWithServices(parsedNetwork, parsedZoning, serviceNetwork, routedServices);

      assertEquals(parsedNetwork.getTransportLayers().size(),1);
      assertEquals(1352, parsedNetwork.getTransportLayers().getFirst().getLinks().size());
      assertEquals(1130, parsedNetwork.getTransportLayers().getFirst().getNodes().size());
      assertEquals(2677, parsedNetwork.getTransportLayers().getFirst().getLinkSegments().size());
      assertEquals(55, parsedNetwork.getTransportLayers().getFirst().getLinkSegmentTypes().size());

      assertEquals(0, parsedZoning.getOdZones().size());
      assertEquals(103, parsedZoning.getTransferZones().size()); // was 142 at one point
      assertEquals(0, parsedZoning.getOdConnectoids().size());
      assertEquals(129, parsedZoning.getTransferConnectoids().size()); // was 191 at one point (probably consolidated)

      assertEquals(serviceNetwork.getTransportLayers().size(),1);
      assertEquals(98, serviceNetwork.getTransportLayers().getFirst().getServiceNodes().size()); // was 100

      /* service nodes correspond to stops which are situated uniquely depending on the side of the road/track. Hence,
       * for now there is an equal number of legs and leg segments ad no bi-directional entries are identified */
      assertEquals(86, serviceNetwork.getTransportLayers().getFirst().getLegSegments().size()); //was 88
      assertEquals(86, serviceNetwork.getTransportLayers().getFirst().getLegs().size()); // was 88

      assertEquals(routedServices.getLayers().size(),1);
      Modes modes = parsedNetwork.getModes();
      assertEquals(53, routedServices.getLayers().getFirst().getServicesByMode(modes.get(BUS)).size());
      assertEquals(2, routedServices.getLayers().getFirst().getServicesByMode(modes.get(LIGHTRAIL)).size());
      assertEquals(8, routedServices.getLayers().getFirst().getServicesByMode(modes.get(TRAIN)).size());
      assertEquals(0, routedServices.getLayers().getFirst().getServicesByMode(modes.get(SUBWAY)).size());
      assertEquals(6, routedServices.getLayers().getFirst().getServicesByMode(modes.get(FERRY)).size());

      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertServiceNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertRoutedServicesFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail("testGtfsIntermodalReaderWithPreExistingPlanitTransferZones");
    }

    System.gc();
  }
}
