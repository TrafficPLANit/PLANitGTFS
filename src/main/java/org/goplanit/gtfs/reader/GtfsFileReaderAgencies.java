package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.enums.GtfsColumnType;
import org.goplanit.gtfs.scheme.GtfsAgenciesScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

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
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderAgencies(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsAgenciesScheme(), gtfsLocation, filePresenceCondition);
  }

  @Override
  protected void initialiseColumnConfiguration(GtfsColumnType columnType) {

  }
}
