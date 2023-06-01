package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.enums.GtfsColumnType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.scheme.GtfsTripsScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS trips. When Column type configuration is set to PLANIT_REQUIRED_COLUMNS we exclude the following columns:
 *  <ul>
 *    <li>WHEELCHAIR_ACCESSIBLE</li>
 *    <li>BIKES_ALLOWED</li>
 *  </ul>
 * 
 * @author markr
 *
 */
public class GtfsFileReaderTrips extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderTrips(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsTripsScheme(), gtfsLocation, filePresenceCondition);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void initialiseColumnConfiguration(GtfsColumnType columnType) {
    switch (columnType){
      case PLANIT_REQUIRED_COLUMNS:
        getSettings().excludeColumns(
            GtfsKeyType.WHEELCHAIR_ACCESSIBLE,
            GtfsKeyType.BIKES_ALLOWED);
        break;
      default:
        super.initialiseColumnConfiguration(columnType);
    }
  }

}
