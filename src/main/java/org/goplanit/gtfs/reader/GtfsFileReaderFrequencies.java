package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.enums.GtfsColumnType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.scheme.GtfsFrequenciesScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS frequencies. When Column type configuration is set to PLANIT_REQUIRED_COLUMNS we still include all columns.
 * 
 * @author markr
 *
 */
public class GtfsFileReaderFrequencies extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderFrequencies(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsFrequenciesScheme(), gtfsLocation, filePresenceCondition);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void initialiseColumnConfiguration(GtfsColumnType columnType) {
    switch (columnType){
      case PLANIT_REQUIRED_COLUMNS:
        // no excluded columns, all relevant
        break;
      default:
        super.initialiseColumnConfiguration(columnType);
    }
  }

}
