package org.planit.gtfs.test;

import org.junit.Assert;
import org.junit.Test;
import org.planit.gtfs.handler.GtfsFileHandlerTrips;
import org.planit.gtfs.reader.GtfsReader;
import org.planit.gtfs.reader.GtfsReaderFactory;
import org.planit.utils.resource.ResourceUtils;

public class BasicGtsTest {
  
  public static final String GTFS_SEQ_DIR = "GTFS/SEQ/SEQ_GTFS.zip";

  @Test
  public void testDefaultGtfsReader() {
           
    try {
      GtfsReader gtfsReader = GtfsReaderFactory.createDefaultReader(ResourceUtils.getResourceUri(GTFS_SEQ_DIR).toURL());     
      gtfsReader.addFileHandler(new GtfsFileHandlerTrips());
      gtfsReader.read();
    } catch (Exception e) {
      Assert.fail();
    }    
  }
}
