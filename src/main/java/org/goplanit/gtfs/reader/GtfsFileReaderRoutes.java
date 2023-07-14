package org.goplanit.gtfs.reader;

import java.net.URL;

import org.goplanit.gtfs.enums.GtfsColumnType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.scheme.GtfsRoutesScheme;
import org.goplanit.gtfs.util.GtfsFileConditions;

/**
 * A GTFS file reader for parsing GTFS routes. When Column type configuration is set to PLANIT_REQUIRED_COLUMNS we exclude the following columns:
 * <ul>
 *   <li>ROUTE_URL</li>
 *   <li>ROUTE_COLOR</li>
 *   <li>ROUTE_TEXT_COLOR</li>
 *   <li>ROUTE_SORT_ORDER</li>
 *   <li>CONTINUOUS_PICKUP</li>
 *   <li>CONTINUOUS_DROP_OFF</li>
 *   <li>any nonGTFS specification column</li>
 * </ul>
 * 
 * @author markr
 *
 */
public class GtfsFileReaderRoutes extends GtfsFileReaderBase {

  /**
   * Constructor
   * 
   * @param gtfsLocation to extract file to parse from (dir or zip file)
   * @param filePresenceCondition on being present
   */
  protected GtfsFileReaderRoutes(URL gtfsLocation, GtfsFileConditions filePresenceCondition) {
    super(new GtfsRoutesScheme(), gtfsLocation, filePresenceCondition);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void initialiseColumnConfiguration(GtfsColumnType columnType) {
    switch (columnType){
      case PLANIT_REQUIRED_COLUMNS:
        getSettings().excludeColumns(
            GtfsKeyType.ROUTE_URL,
            GtfsKeyType.ROUTE_COLOR,
            GtfsKeyType.ROUTE_TEXT_COLOR,
            GtfsKeyType.ROUTE_SORT_ORDER,
            GtfsKeyType.CONTINUOUS_PICKUP,
            GtfsKeyType.CONTINUOUS_DROP_OFF);
        break;
      default:
        super.initialiseColumnConfiguration(columnType);
    }
  }
}
