package org.goplanit.gtfs.test;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.handler.*;
import org.goplanit.gtfs.reader.GtfsFileReaderAgencies;
import org.goplanit.gtfs.reader.GtfsFileReaderTrips;
import org.goplanit.gtfs.reader.GtfsReader;
import org.goplanit.gtfs.reader.GtfsReaderFactory;
import org.goplanit.gtfs.scheme.GtfsFileSchemeFactory;
import org.goplanit.gtfs.test.handler.GtfsFileHandlerTripsTest;
import org.goplanit.logging.Logging;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.resource.ResourceUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Gtfs's API basic functionality
 * 
 * @author markr
 *
 */
public class BasicGtfsTest {

  private static Logger LOGGER;

  private static final Path RESOURCES_GTFS = Path.of("src","test","resources","GTFS");

  //public static final Path GTFS_SEQ_ALL = Path.of(ResourceUtils.getResourceUri(Path.of("GTFS","SEQ","SEQGTFS.zip").toString()));
  public static final Path GTFS_SEQ_ALL = Path.of(RESOURCES_GTFS.toString(),"SEQ","SEQGTFS.zip");


  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(BasicGtfsTest.class);
    }
  }

  @AfterAll
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
      GtfsReader gtfsReader = GtfsReaderFactory.createDefaultReader(GTFS_SEQ_ALL.toUri().toURL());
      
      /* register all possible handlers where we note that reader is returned when handler is registered*/
      @SuppressWarnings("unused")
      GtfsFileReaderAgencies agencyFileReader = (GtfsFileReaderAgencies) gtfsReader.addFileHandler(new GtfsFileHandlerAgency());
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerAttributions())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerCalendarDates())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerCalendars())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerFareAttributes())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerFareRules())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerFeedInfo())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerFrequencies())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerLevels())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerPathways())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerRoutes())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerShapes())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerStops())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerStopTimes())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerTransfers())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerTranslations())!=null);
      assert(gtfsReader.addFileHandler(new GtfsFileHandlerTrips())!=null);
      
      /* should be able to parse all data (without doing anything) */
      gtfsReader.read(StandardCharsets.UTF_8);
      
      System.gc();
    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      fail("testDefaultGtfsReader");
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
          GtfsFileSchemeFactory.create(GtfsFileType.TRIPS), GTFS_SEQ_ALL.toUri().toURL());
      tripsFileReader.addHandler(tripsHandler);
      
      tripsFileReader.getSettings().excludeColumns(GtfsKeyType.TRIP_HEADSIGN);
      tripsFileReader.read(StandardCharsets.UTF_8);
      
      assertNotNull(tripsHandler.trips);
      assertEquals(156225,tripsHandler.trips.size());
      assertFalse(tripsHandler.trips.values().iterator().next().containsKey(GtfsKeyType.TRIP_HEADSIGN));
      
    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      fail("testExcludingTripsColumns");
    }

    System.gc();
  }


}
