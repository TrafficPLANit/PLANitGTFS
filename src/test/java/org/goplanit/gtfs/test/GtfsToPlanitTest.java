package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderFactory;
import org.goplanit.gtfs.converter.service.GtfsServicesReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderFactory;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReader;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderSettings;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Quadruple;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.resource.ResourceUtils;
import org.goplanit.zoning.Zoning;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
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

    //Prep PLANit memory model
    final String PLANIT_SYDNEY_INTERMODAL_NETWORK_DIR = Path.of("planit","sydney").toString();
    String INPUT_PATH = Path.of(ResourceUtils.getResourceUri(PLANIT_SYDNEY_INTERMODAL_NETWORK_DIR)).toAbsolutePath().toString();

    /* parse PLANit intermodal network from disk to memory */
    PlanitIntermodalReader planitReader = PlanitIntermodalReaderFactory.create(
            new PlanitIntermodalReaderSettings(INPUT_PATH));
    var planitIntermodalNetworkTuple = planitReader.read();
    macroscopicNetwork = planitIntermodalNetworkTuple.first();
    zoning = planitIntermodalNetworkTuple.second();
  }

  @AfterClass
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
    IdGenerator.reset();
  }

  /**
   * Test that attempts to extract PLANit routed services from GTFS data
   */
  @Test
  public void testGtfsRoutedServicesReader() {

    try {
      String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

      GtfsServicesReader servicesReader = GtfsServicesReaderFactory.create(
          GTFS_FILES_DIR, CountryNames.AUSTRALIA, macroscopicNetwork, RouteTypeChoice.EXTENDED);
      Pair<ServiceNetwork,RoutedServices> servicesPair = servicesReader.read();

      var serviceNetwork = servicesPair.first();
      var routedServices = servicesPair.second();
      assertThat(serviceNetwork.getTransportLayers().size(),equalTo(1));

      /* service nodes correspond to stops which are situated uniquely depending on the side of the road/track. Hence,
       * for now there is an equal number of legs and leg segments ad no bi-directional entries are identified */
      assertThat(serviceNetwork.getTransportLayers().getFirst().getServiceNodes().size(),equalTo(58478));
      assertThat(serviceNetwork.getTransportLayers().getFirst().getLegSegments().size(),equalTo(93608));
      assertThat(serviceNetwork.getTransportLayers().getFirst().getLegs().size(),equalTo(93608));
      assert(serviceNetwork.getTransportLayers().getFirst().getSupportedModes().containsAll(servicesReader.getSettings().getAcivatedPlanitModes()));

      assertThat(routedServices.getLayers().size(),equalTo(1));
      Modes modes = macroscopicNetwork.getModes();
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(BUS)).size(),equalTo(8150));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(LIGHTRAIL)).size(),equalTo(5));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(TRAIN)).size(),equalTo(42));
      assertThat(routedServices.getLayers().getFirst().getServicesByMode(modes.get(SUBWAY)).size(),equalTo(1));

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail();
    }
  }

  /**
   * Test that attempts to extract PLANit routed services, service network and zoning  from GTFS data
   */
  @Test
  public void testGtfsIntermodalReader() {

    try {
      String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

      var gtfsIntermodalReader = GtfsIntermodalReaderFactory.create(
          GTFS_FILES_DIR, CountryNames.AUSTRALIA, macroscopicNetwork, zoning, RouteTypeChoice.EXTENDED);
      Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork,RoutedServices> result = gtfsIntermodalReader.readWithServices();

      var serviceNetwork = result.third();
      var routedServices = result.fourth();

      // todo: add assertions

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail();
    }
  }
}
