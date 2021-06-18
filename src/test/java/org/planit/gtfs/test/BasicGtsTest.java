package org.planit.gtfs.test;

import org.junit.Test;
import org.planit.gtfs.reader.GtfsFileHandlerTrips;
import org.planit.gtfs.reader.GtfsReader;
import org.planit.gtfs.reader.GtfsReaderFactory;

public class BasicGtsTest {

  @Test
  public void testDefaultGtfsReader() {
    GtfsReader gtfsReader = GtfsReaderFactory.createDefaultReader();
    gtfsReader.addFileHandler(new GtfsFileHandlerTrips());
    gtfsReader.read();
  }
}
