package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.enums.GtfsColumnType;
import org.goplanit.gtfs.scheme.GtfsAgenciesScheme;

/**
 * A GTFS file reader for parsing GTFS agencies
 * 
 * @author markr
 *
 */
public class GtfsFileReaderAgencies extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   */
  protected GtfsFileReaderAgencies(URL gtfsLocation) {
    super(new GtfsAgenciesScheme(), gtfsLocation);
  }

  @Override
  protected void initialiseColumnConfiguration(GtfsColumnType columnType) {

  }
}
