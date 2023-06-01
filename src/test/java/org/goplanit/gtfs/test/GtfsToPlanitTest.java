package org.goplanit.gtfs.test;

import org.goplanit.converter.idmapping.IdMapperType;
import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderFactory;
import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderSettings;
import org.goplanit.gtfs.converter.service.GtfsServicesReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderFactory;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.gtfs.test.utils.SydneyGtfsServicesSettingsUtils;
import org.goplanit.gtfs.test.utils.SydneyGtfsZoningSettingsUtils;
import org.goplanit.io.converter.intermodal.*;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Quadruple;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.resource.ResourceUtils;
import org.goplanit.zoning.Zoning;
import org.junit.jupiter.api.*;

import java.net.URL;
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
public class GtfsToPlanitTest {

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
      LOGGER = Logging.createLogger(GtfsToPlanitTest.class);
    }

    LOGGER.setLevel(Level.SEVERE);
    //Prep PLANit memory model
    final String PLANIT_SYDNEY_INTERMODAL_NETWORK_DIR = Path.of("planit","sydney").toString();
    String INPUT_PATH = Path.of(ResourceUtils.getResourceUri(PLANIT_SYDNEY_INTERMODAL_NETWORK_DIR)).toAbsolutePath().toString();

    /* parse PLANit intermodal network from disk to memory */
    PlanitIntermodalReader planitReader = PlanitIntermodalReaderFactory.create(new PlanitIntermodalReaderSettings(INPUT_PATH));
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

  @BeforeEach
  public void before() throws PlanItException {
    LOGGER.setLevel(Level.SEVERE);
    //Prep PLANit memory model
    final String PLANIT_SYDNEY_INTERMODAL_NETWORK_DIR = Path.of("planit","sydney").toString();
    String INPUT_PATH = Path.of(ResourceUtils.getResourceUri(PLANIT_SYDNEY_INTERMODAL_NETWORK_DIR)).toAbsolutePath().toString();

    /* parse PLANit intermodal network from disk to memory */
    PlanitIntermodalReader planitReader = PlanitIntermodalReaderFactory.create(new PlanitIntermodalReaderSettings(INPUT_PATH));
    var planitIntermodalNetworkTuple = planitReader.read();
    macroscopicNetwork = planitIntermodalNetworkTuple.first();
    zoning = planitIntermodalNetworkTuple.second();
    LOGGER.setLevel(Level.INFO);
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
  public void testGtfsRoutedServicesReader() {

    try {
      //String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();
      String GTFS_FILES_DIR = GTFS_NSW_NO_SHAPES.toString();

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

      assertEquals(routedServices.getLayers().size(),1);
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(macroscopicNetwork.getModes().get(BUS)).size(),7903);
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(macroscopicNetwork.getModes().get(LIGHTRAIL)).size(),5);
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(macroscopicNetwork.getModes().get(TRAIN)).size(),39);
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(macroscopicNetwork.getModes().get(SUBWAY)).size(),0);
      assertEquals(routedServices.getLayers().getFirst().getServicesByMode(macroscopicNetwork.getModes().get(FERRY)).size(),23);

    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail("testGtfsRoutedServicesReader");
    }

    System.gc();
  }

  /**
   * Test that attempts to extract PLANit routed services, service network and zoning from GTFS data given the PLANit network exists,
   * but it has no transfer zones yet,i.e., all transfer zones (stops) are to be based on the GTFS data only.
   */
  @Test
  public void testGtfsIntermodalReaderWithoutPreExistingPlanitTransferZones() {

    try {
      //String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();
      var GTFS_FILES_DIR = GTFS_NSW_NO_SHAPES.toString();

      /* construct intermodal reader without pre-existing zoning */
      var gtfsIntermodalReader = GtfsIntermodalReaderFactory.create(
          GTFS_FILES_DIR, CountryNames.AUSTRALIA, DayOfWeek.THURSDAY, macroscopicNetwork, RouteTypeChoice.EXTENDED);

      /* 6-10 in the morning as time period filter */
      gtfsIntermodalReader.getSettings().getServiceSettings().addTimePeriodFilter(
          LocalTime.of(6,0,0),
          LocalTime.of(9, 59,59));

      // log mappings, useful for debugging if needed
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogMappedGtfsZones(true);
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogCreatedGtfsZones(true);

      SydneyGtfsZoningSettingsUtils.minimiseVerifiedWarnings(gtfsIntermodalReader.getSettings().getZoningSettings());
      SydneyGtfsServicesSettingsUtils.minimiseVerifiedWarnings(gtfsIntermodalReader.getSettings().getServiceSettings());

      Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> result = gtfsIntermodalReader.readWithServices();

      var network = result.first();
      var zoning = result.second();
      var serviceNetwork = result.third();
      var routedServices = result.fourth();

      //todo: it is not manually verified the below numbers are correct, but if this fails, we at least know something has changed in how we process the same underlying data
      // and a conscious choice has to be made whether this is better or not before changing the below results
      assertEquals(1, network.getTransportLayers().size());
      assertEquals(1381, network.getTransportLayers().getFirst().getLinks().size());
      assertEquals(1159, network.getTransportLayers().getFirst().getNodes().size());
      assertEquals(2731, network.getTransportLayers().getFirst().getLinkSegments().size());
      assertEquals(71, network.getTransportLayers().getFirst().getLinkSegmentTypes().size());

      assertEquals(0, zoning.getOdZones().size());
      assertEquals(118, zoning.getTransferZones().size());
      assertEquals(0, zoning.getOdConnectoids().size());
      assertEquals(148, zoning.getTransferConnectoids().size());

      assertEquals(serviceNetwork.getTransportLayers().size(),1);
      assertEquals(serviceNetwork.getTransportLayers().getFirst().getServiceNodes().size(),97);

      /* service nodes correspond to stops which are situated uniquely depending on the side of the road/track. Hence,
       * for now there is an equal number of legs and leg segments ad no bi-directional entries are identified */
      assertEquals(serviceNetwork.getTransportLayers().getFirst().getLegSegments().size(),81);
      assertEquals(serviceNetwork.getTransportLayers().getFirst().getLegs().size(),81);

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
   * Test that attempts to extract PLANit routed services, service network and zoning  from GTFS data given the PLANit network already has existing transfer
   * zones present that will be fused/merged when found in GTFS
   */
  @Test
  public void testGtfsIntermodalReaderWithPreExistingPlanitTransferZones() {

    try {
      //String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES.toString())).toAbsolutePath().toString();
      var GTFS_FILES_DIR = GTFS_NSW_NO_SHAPES.toString();

      var gtfsIntermodalReader = GtfsIntermodalReaderFactory.create(
          GTFS_FILES_DIR, CountryNames.AUSTRALIA, DayOfWeek.THURSDAY, macroscopicNetwork, zoning, RouteTypeChoice.EXTENDED);

      /* 6-10 in the morning as time period filter */
      gtfsIntermodalReader.getSettings().getServiceSettings().addTimePeriodFilter(
          LocalTime.of(6,0,0),
          LocalTime.of(9, 59,59));

      // log mappings, useful for debugging if needed
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogMappedGtfsZones(true);
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogCreatedGtfsZones(true);

      SydneyGtfsZoningSettingsUtils.minimiseVerifiedWarnings(gtfsIntermodalReader.getSettings().getZoningSettings());
      SydneyGtfsServicesSettingsUtils.minimiseVerifiedWarnings(gtfsIntermodalReader.getSettings().getServiceSettings());

      // manual overrides
      gtfsIntermodalReader.getSettings().getZoningSettings().setOverwriteGtfsStopTransferZoneMapping(
          "200059","3814704459", IdMapperType.EXTERNAL_ID); // Museum of Sydney

      Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> result = gtfsIntermodalReader.readWithServices();

      var network = result.first();
      var zoning = result.second();
      var serviceNetwork = result.third();
      var routedServices = result.fourth();

      //todo: it is not manually verified the below numbers are correct, but if this fails, we at least know something has changed in how we process the same underlying data
      // and a conscious choice has to be amde whether this is better or not before changing the below results
      assertEquals(network.getTransportLayers().size(),1);
      assertEquals(1354, network.getTransportLayers().getFirst().getLinks().size());
      assertEquals(1132, network.getTransportLayers().getFirst().getNodes().size());
      assertEquals(2677, network.getTransportLayers().getFirst().getLinkSegments().size());
      assertEquals(71, network.getTransportLayers().getFirst().getLinkSegmentTypes().size());

      assertEquals(0, zoning.getOdZones().size());
      assertEquals(143, zoning.getTransferZones().size());
      assertEquals(0, zoning.getOdConnectoids().size());
      assertEquals(181, zoning.getTransferConnectoids().size());

      assertEquals(serviceNetwork.getTransportLayers().size(),1);
      assertEquals(100, serviceNetwork.getTransportLayers().getFirst().getServiceNodes().size());

      /* service nodes correspond to stops which are situated uniquely depending on the side of the road/track. Hence,
       * for now there is an equal number of legs and leg segments ad no bi-directional entries are identified */
      assertEquals(88, serviceNetwork.getTransportLayers().getFirst().getLegSegments().size());
      assertEquals(88, serviceNetwork.getTransportLayers().getFirst().getLegs().size());

      assertEquals(routedServices.getLayers().size(),1);
      Modes modes = macroscopicNetwork.getModes();
      assertEquals(53, routedServices.getLayers().getFirst().getServicesByMode(modes.get(BUS)).size());
      assertEquals(2, routedServices.getLayers().getFirst().getServicesByMode(modes.get(LIGHTRAIL)).size());
      assertEquals(8, routedServices.getLayers().getFirst().getServicesByMode(modes.get(TRAIN)).size());
      assertEquals(0, routedServices.getLayers().getFirst().getServicesByMode(modes.get(SUBWAY)).size());
      assertEquals(6, routedServices.getLayers().getFirst().getServicesByMode(modes.get(FERRY)).size());

    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail("testGtfsIntermodalReaderWithPreExistingPlanitTransferZones");
    }

    System.gc();
  }
}
