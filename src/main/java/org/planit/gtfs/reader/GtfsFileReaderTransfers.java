package org.planit.gtfs.reader;

import java.net.URL;

import org.planit.gtfs.scheme.GtfsTransfersScheme;

/**
 * A GTFS file reader for parsing GTFS transfers entries
 * 
 * @author markr
 *
 */
public class GtfsFileReaderTransfers extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  public GtfsFileReaderTransfers(URL gtfsLocation) {
    super(new GtfsTransfersScheme(), gtfsLocation);
  }

}
