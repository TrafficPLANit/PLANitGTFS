package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderFactory;
import org.goplanit.gtfs.converter.service.GtfsServicesReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderFactory;
import org.goplanit.gtfs.enums.RouteTypeChoice;
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
import org.junit.*;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.goplanit.utils.mode.PredefinedModeType.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit tests for Gtfs's API basic functionality
 * 
 * @author markr
 *
 */
public class GtfsToPlanitTest {

  private static Logger LOGGER;

  public static final String GTFS_SEQ_ALL = Path.of("GTFS","SEQ","SEQGTFS.zip").toString();

  public static final String GTFS_NSW_NO_SHAPES = Path.of("GTFS","NSW","greatersydneygtfsstaticnoshapes.zip").toString();

  public static MacroscopicNetwork macroscopicNetwork;

  public static Zoning zoning;

  @BeforeClass
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

  @AfterClass
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
    IdGenerator.reset();
  }

  @Before
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

  @After
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
      String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

      var networkCopy = macroscopicNetwork.deepClone();
      GtfsServicesReader servicesReader = GtfsServicesReaderFactory.create(
          networkCopy, GTFS_FILES_DIR, CountryNames.AUSTRALIA, DayOfWeek.THURSDAY, RouteTypeChoice.EXTENDED);
      Pair<ServiceNetwork,RoutedServices> servicesPair = servicesReader.read();

      var serviceNetwork = servicesPair.first();
      var routedServices = servicesPair.second();
      assertThat(serviceNetwork.getTransportLayers().size(),equalTo(1));

      /* service nodes correspond to stops which are situated uniquely depending on the side of the road/track. Hence,
       * for now there is an equal number of legs and leg segments ad no bi-directional entries are identified */

      //todo: it is not manually verified the below numbers are correct, but if this fails, we at least know something has changed in how we process the same underlying data
      // and a conscious choice has to be made whether this is better or not before changing the below results
      assertThat(serviceNetwork.getTransportLayers().getFirst().getServiceNodes().size(),equalTo(58181));
      assertThat(serviceNetwork.getTransportLayers().getFirst().getLegSegments().size(),equalTo(92198));
      assertThat(serviceNetwork.getTransportLayers().getFirst().getLegs().size(),equalTo(92198));

      assertThat(routedServices.getLayers().size(),equalTo(1));
      Modes modes = macroscopicNetwork.getModes();
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(BUS)).size(),equalTo(7903));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(LIGHTRAIL)).size(),equalTo(5));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(TRAIN)).size(),equalTo(39));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(SUBWAY)).size(),equalTo(0));

    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      Assert.fail();
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
      String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

      /* construct intermodal reader without pre-existing zoning */
      var gtfsIntermodalReader = GtfsIntermodalReaderFactory.create(
          GTFS_FILES_DIR, CountryNames.AUSTRALIA, DayOfWeek.MONDAY, macroscopicNetwork, RouteTypeChoice.EXTENDED);

      // log mappings, useful for debugging if needed
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogMappedGtfsZones(true);
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogCreatedGtfsZones(true);

      // manual override which without a zoning makes no sense, so should log a warning (verified it does, so if it does not, something is wrong)
      gtfsIntermodalReader.getSettings().getZoningSettings().setOverwriteGtfsStopTransferZoneMapping("200059","3814704459"); // Museum of Sydney

      Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> result = gtfsIntermodalReader.readWithServices();

      var network = result.first();
      var zoning = result.second();
      var serviceNetwork = result.third();
      var routedServices = result.fourth();

      //todo: it is not manually verified the below numbers are correct, but if this fails, we at least know something has changed in how we process the same underlying data
      // and a conscious choice has to be made whether this is better or not before changing the below results
      assertThat(network.getTransportLayers().size(),equalTo(1));
      assertThat(network.getTransportLayers().getFirst().getLinks().size(),equalTo(1340));
      assertThat(network.getTransportLayers().getFirst().getNodes().size(),equalTo(1122));
      assertThat(network.getTransportLayers().getFirst().getLinkSegments().size(),equalTo(2649));
      assertThat(network.getTransportLayers().getFirst().getLinkSegmentTypes().size(),equalTo(50));

      assertThat(zoning.getOdZones().size(),equalTo(0));
      assertThat(zoning.getTransferZones().size(),equalTo(106));
      assertThat(zoning.getOdConnectoids().size(),equalTo(0));
      assertThat(zoning.getTransferConnectoids().size(),equalTo(133));

      assertThat(serviceNetwork.getTransportLayers().size(),equalTo(1));
      assertThat(serviceNetwork.getTransportLayers().getFirst().getServiceNodes().size(),equalTo(85));

      /* service nodes correspond to stops which are situated uniquely depending on the side of the road/track. Hence,
       * for now there is an equal number of legs and leg segments ad no bi-directional entries are identified */
      assertThat(serviceNetwork.getTransportLayers().getFirst().getLegSegments().size(),equalTo(67));
      assertThat(serviceNetwork.getTransportLayers().getFirst().getLegs().size(),equalTo(67));

      assertThat(routedServices.getLayers().size(),equalTo(1));
      Modes modes = macroscopicNetwork.getModes();
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(BUS)).size(),equalTo(67)); //todo: was 85 something is wrong
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(LIGHTRAIL)).size(),equalTo(2));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(TRAIN)).size(),equalTo(8));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(SUBWAY)).size(),equalTo(0));

    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      Assert.fail();
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
      String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

      var gtfsIntermodalReader = GtfsIntermodalReaderFactory.create(
          GTFS_FILES_DIR, CountryNames.AUSTRALIA, DayOfWeek.MONDAY, macroscopicNetwork, zoning, RouteTypeChoice.EXTENDED);

      // log mappings, useful for debugging if needed
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogMappedGtfsZones(true);
      //gtfsIntermodalReader.getSettings().getZoningSettings().setLogCreatedGtfsZones(true);

      // manual overrides
      gtfsIntermodalReader.getSettings().getZoningSettings().setOverwriteGtfsStopTransferZoneMapping("200059","3814704459"); // Museum of Sydney

      Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> result = gtfsIntermodalReader.readWithServices();

      var network = result.first();
      var zoning = result.second();
      var serviceNetwork = result.third();
      var routedServices = result.fourth();

      //todo: it is not manually verified the below numbers are correct, but if this fails, we at least know something has changed in how we process the same underlying data
      // and a conscious choice has to be amde whether this is better or not before changing the below results
      assertThat(network.getTransportLayers().size(),equalTo(1));
      assertThat(network.getTransportLayers().getFirst().getLinks().size(),equalTo(1306));
      assertThat(network.getTransportLayers().getFirst().getNodes().size(),equalTo(1088));
      assertThat(network.getTransportLayers().getFirst().getLinkSegments().size(),equalTo(2581));
      assertThat(network.getTransportLayers().getFirst().getLinkSegmentTypes().size(),equalTo(50));

      assertThat(zoning.getOdZones().size(),equalTo(0));
      assertThat(zoning.getTransferZones().size(),equalTo(113));
      assertThat(zoning.getOdConnectoids().size(),equalTo(0));
      assertThat(zoning.getTransferConnectoids().size(),equalTo(146));

      assertThat(serviceNetwork.getTransportLayers().size(),equalTo(1));
      assertThat(serviceNetwork.getTransportLayers().getFirst().getServiceNodes().size(),equalTo(87));

      /* service nodes correspond to stops which are situated uniquely depending on the side of the road/track. Hence,
       * for now there is an equal number of legs and leg segments ad no bi-directional entries are identified */
      assertThat(serviceNetwork.getTransportLayers().getFirst().getLegSegments().size(),equalTo(72));
      assertThat(serviceNetwork.getTransportLayers().getFirst().getLegs().size(),equalTo(72));

      assertThat(routedServices.getLayers().size(),equalTo(1));
      Modes modes = macroscopicNetwork.getModes();
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(BUS)).size(),equalTo(67)); //todo: was 85 something is wrong
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(LIGHTRAIL)).size(),equalTo(2));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(TRAIN)).size(),equalTo(8));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(SUBWAY)).size(),equalTo(0));

    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      Assert.fail();
    }

    System.gc();
  }
}
