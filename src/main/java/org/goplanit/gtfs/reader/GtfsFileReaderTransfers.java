package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.scheme.GtfsTransfersScheme;

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
  protected GtfsFileReaderTransfers(URL gtfsLocation) {
    super(new GtfsTransfersScheme(), gtfsLocation);
  }

}
