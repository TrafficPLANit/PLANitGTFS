package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.enums.GtfsColumnType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.scheme.GtfsStopTimesScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS stop times. When Column type configuration is set to PLANIT_REQUIRED_COLUMNS we exclude the following columns:
 *  <ul>
 *    <li>STOP_HEADSIGN</li>
 *  </ul>
 * 
 * @author markr
 *
 */
public class GtfsFileReaderStopTimes extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderStopTimes(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsStopTimesScheme(), gtfsLocation, filePresenceCondition);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void initialiseColumnConfiguration(GtfsColumnType columnType) {
    switch (columnType){
      case PLANIT_REQUIRED_COLUMNS:
        getSettings().excludeColumns(
            GtfsKeyType.STOP_HEADSIGN);
        break;
      default:
        super.initialiseColumnConfiguration(columnType);
    }
  }

}
