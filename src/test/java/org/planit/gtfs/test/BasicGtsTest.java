package org.planit.gtfs.test;

import org.junit.Assert;
import org.junit.Test;
import org.planit.gtfs.handler.*;
import org.planit.gtfs.reader.GtfsReader;
import org.planit.gtfs.reader.GtfsReaderFactory;
import org.planit.utils.resource.ResourceUtils;

public class BasicGtsTest {
  
  public static final String GTFS_SEQ_DIR = "GTFS/SEQ/SEQ_GTFS.zip";

  @Test
  public void testDefaultGtfsReader() {
           
    try {
      GtfsReader gtfsReader = GtfsReaderFactory.createDefaultReader(ResourceUtils.getResourceUri(GTFS_SEQ_DIR).toURL());     
      
      /* register all possible handlers */
      gtfsReader.addFileHandler(new GtfsFileHandlerAgency());
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
}
