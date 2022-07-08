package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.zoning.GtfsPublicTransportReaderSettings;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderFactory;
import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.handler.*;
import org.goplanit.gtfs.reader.GtfsFileReaderAgencies;
import org.goplanit.gtfs.reader.GtfsFileReaderTrips;
import org.goplanit.gtfs.reader.GtfsReader;
import org.goplanit.gtfs.reader.GtfsReaderFactory;
import org.goplanit.gtfs.scheme.GtfsFileSchemeFactory;
import org.goplanit.gtfs.test.handler.GtfsFileHandlerTripsTest;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReader;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderSettings;
import org.goplanit.logging.Logging;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.resource.ResourceUtils;
import org.junit.*;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Unit tests for Gtfs's API basic functionality
 * 
 * @author markr
 *
 */
public class BasicGtfsTest {

  private static Logger LOGGER;

  public static final String GTFS_SEQ_ALL = Path.of("GTFS","SEQ","SEQ_GTFS.zip").toString();

  public static final String GTFS_NSW_NO_SHAPES = Path.of("GTFS","NSW","greater_sydney_gtfs_static_no_shapes.zip").toString();

  public static final String PLANIT_SYDNEY_INTERMODAL_NETWORK_DIR = Path.of("planit","sydney").toString();

  @BeforeClass
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(BasicGtfsTest.class);
    }
  }

  @AfterClass
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
    IdGenerator.reset();
  }

  /**
   * Test if umbrella reader with all file types activated runs properly
   */
  @Test
  public void testDefaultGtfsReader() {
           
    try {
      GtfsReader gtfsReader = GtfsReaderFactory.createDefaultReader(ResourceUtils.getResourceUrl(GTFS_SEQ_ALL));
      
      /* register all possible handlers where we note that reader is returned when handler is registered*/
      @SuppressWarnings("unused")
      GtfsFileReaderAgencies agencyFileReader = (GtfsFileReaderAgencies) gtfsReader.addFileHandler(new GtfsFileHandlerAgency());
      gtfsReader.addFileHandler(new GtfsFileHandlerAttributions());
      gtfsReader.addFileHandler(new GtfsFileHandlerCalendarDates());
      gtfsReader.addFileHandler(new GtfsFileHandlerCalendars());
      gtfsReader.addFileHandler(new GtfsFileHandlerFareAttributes());
      gtfsReader.addFileHandler(new GtfsFileHandlerFareRules());
      gtfsReader.addFileHandler(new GtfsFileHandlerFeedInfo());
      gtfsReader.addFileHandler(new GtfsFileHandlerFrequencies());
      gtfsReader.addFileHandler(new GtfsFileHandlerLevels());
      gtfsReader.addFileHandler(new GtfsFileHandlerPathways());
      gtfsReader.addFileHandler(new GtfsFileHandlerRoutes());
      gtfsReader.addFileHandler(new GtfsFileHandlerShapes());
      gtfsReader.addFileHandler(new GtfsFileHandlerStops());
      gtfsReader.addFileHandler(new GtfsFileHandlerStopTimes());
      gtfsReader.addFileHandler(new GtfsFileHandlerTransfers());
      gtfsReader.addFileHandler(new GtfsFileHandlerTranslations());
      gtfsReader.addFileHandler(new GtfsFileHandlerTrips());
      
      /* should be able to parse all data (without doing anything) */
      gtfsReader.read();
    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      Assert.fail();
    }    
  }
  
  /**
   * Test number of trips is parsed correctly as well as excluding columns works
   */
  @Test
  public void testExcludingTripsColumns() {
    
    try {         
      GtfsFileHandlerTripsTest tripsHandler = new GtfsFileHandlerTripsTest();
      
      GtfsFileReaderTrips tripsFileReader  =(GtfsFileReaderTrips) GtfsReaderFactory.createFileReader(
          GtfsFileSchemeFactory.create(GtfsFileType.TRIPS), ResourceUtils.getResourceUri(GTFS_SEQ_ALL).toURL());
      tripsFileReader.addHandler(tripsHandler);
      
      tripsFileReader.getSettings().excludeColumns(GtfsKeyType.TRIP_HEADSIGN);
      tripsFileReader.read();
      
      assertNotNull(tripsHandler.trips);
      assertEquals(tripsHandler.trips.size(), 156225);
      assertFalse(tripsHandler.trips.values().iterator().next().containsKey(GtfsKeyType.TRIP_HEADSIGN));
      
    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      Assert.fail();
    }
  }

  /**
   * Test that attempts to supplement a PLANit network parsed from disk into memory with GTFS information through a PLANit GTFS converter/reader
   */
  @Test
  public void testWithPlanitZoningReader() {

    try {
      String INPUT_PATH = Path.of(ResourceUtils.getResourceUri(PLANIT_SYDNEY_INTERMODAL_NETWORK_DIR)).toAbsolutePath().toString();
      String GTFS_STOPS_FILE = Path.of(ResourceUtils.getResourceUri(GTFS_NSW_NO_SHAPES)).toAbsolutePath().toString();

      /* parse PLANit intermodal network from disk to memory */
      PlanitIntermodalReader planitReader = PlanitIntermodalReaderFactory.create(
              new PlanitIntermodalReaderSettings(INPUT_PATH));
      var planitIntermodalNetworkTuple = planitReader.read();

      /* augment zoning with GTFS */
      final var gtfsReader = GtfsZoningReaderFactory.create(
          new GtfsPublicTransportReaderSettings(GTFS_STOPS_FILE, CountryNames.AUSTRALIA, planitIntermodalNetworkTuple.first()),
          planitIntermodalNetworkTuple.second());
      //gtfsReader.getSettings().setGtfsStopToTransferZoneSearchRadiusMeters(50);
      gtfsReader.read();

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail();
    }
  }
}
