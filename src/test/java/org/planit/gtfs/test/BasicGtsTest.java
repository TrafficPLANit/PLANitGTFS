package org.planit.gtfs.test;

import org.junit.Assert;
import org.junit.Test;
import org.planit.gtfs.enums.GtfsKeyType;
import org.planit.gtfs.handler.*;
import org.planit.gtfs.model.GtfsAgency;
import org.planit.gtfs.reader.GtfsFileReaderAgencies;
import org.planit.gtfs.reader.GtfsFileReaderTrips;
import org.planit.gtfs.reader.GtfsReader;
import org.planit.gtfs.reader.GtfsReaderFactory;
import org.planit.gtfs.scheme.GtfsFileScheme;
import org.planit.gtfs.scheme.GtfsTripsScheme;
import org.planit.utils.resource.ResourceUtils;

public class BasicGtsTest {
  
  public static final String GTFS_SEQ_DIR = "GTFS/SEQ/SEQ_GTFS.zip";

  @Test
  public void testDefaultGtfsReader() {
           
    try {
      GtfsReader gtfsReader = GtfsReaderFactory.createDefaultReader(ResourceUtils.getResourceUri(GTFS_SEQ_DIR).toURL());     
      
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
      Assert.fail();
    }    
  }
  
  @Test
  public void testExcludingColumns() {
    
    try {         
      GtfsFileReaderTrips tripsFileReader  =(GtfsFileReaderTrips) GtfsReaderFactory.createFileReader(new GtfsTripsScheme(), ResourceUtils.getResourceUri(GTFS_SEQ_DIR).toURL());
      tripsFileReader.addHandler(new GtfsFileHandlerTrips());
      tripsFileReader.getSettings().excludeColumns(GtfsKeyType.TRIP_HEADSIGN);
      tripsFileReader.read();
      
      //TODO test  if headsign is indeed not present -> create test handler
    } catch (Exception e) {
      Assert.fail();
    }     
    
  }
}
