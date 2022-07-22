package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.service.GtfsServicesReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderFactory;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderFactory;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReader;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderSettings;
import org.goplanit.io.converter.service.PlanitRoutedServicesReader;
import org.goplanit.io.converter.service.PlanitRoutedServicesReaderFactory;
import org.goplanit.io.converter.service.PlanitServiceNetworkReader;
import org.goplanit.io.converter.service.PlanitServiceNetworkReaderFactory;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.resource.ResourceUtils;
import org.goplanit.zoning.Zoning;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Unit tests for Gtfs's API basic functionality
 * 
 * @author markr
 *
 */
public class GtfsToPlanitTest {

  private static Logger LOGGER;

  public static final String GTFS_SEQ_ALL = Path.of("GTFS","SEQ","SEQ_GTFS.zip").toString();

  public static final String GTFS_NSW_NO_SHAPES = Path.of("GTFS","NSW","greater_sydney_gtfs_static_no_shapes.zip").toString();

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
   * Test that attempts to supplement a PLANit network parsed from disk into memory with GTFS information through a PLANit GTFS converter/reader
   */
  @Test
  public void testWithPlanitZoningReader() {

    //TODO: needs work as we cannot do it this way anymore --> need to think about hwo to refactor
    //      this and then setup test after we have some experience parsing various other files other than
    //      just stops (we need other files to identify what modes a GTFS stop supports in order to match stops
    //      to PLANit transfer zones using geographic closeness

    try {
      String GTFS_STOPS_FILE = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

      /* augment zoning with GTFS */
      final var gtfsReader = GtfsZoningReaderFactory.create(
          new GtfsZoningReaderSettings(GTFS_STOPS_FILE, CountryNames.AUSTRALIA, macroscopicNetwork), zoning);
      //gtfsReader.getSettings().setGtfsStopToTransferZoneSearchRadiusMeters(50);
      gtfsReader.read();

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail();
    }
  }

  /**
   * Test that attempts to extract PLANit routed services from GTFS data
   */
  @Test
  public void testGtfsRoutedServicesReader() {

    try {
      String GTFS_FILES_DIR = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

      GtfsServicesReader servicesReader = GtfsServicesReaderFactory.create(GTFS_FILES_DIR, macroscopicNetwork);
      Pair<ServiceNetwork,RoutedServices> servicesPair = servicesReader.read();

      /* populate service network with GTFS routed services */
      final var gtfsReader = GtfsServicesReaderFactory.create(GTFS_FILES_DIR, macroscopicNetwork);
      //gtfsReader.getSettings().setGtfsStopToTransferZoneSearchRadiusMeters(50);
      var resultPair = gtfsReader.read();

      //todo add assertions

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail();
    }
  }
}
