package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.converter.diagnostics.GtfsEntityScope;
import org.goplanit.gtfs.converter.diagnostics.GtfsParseIssue;
import org.goplanit.gtfs.converter.diagnostics.GtfsScopeDimension;
import org.goplanit.gtfs.converter.diagnostics.GtfsScopeState;
import org.goplanit.gtfs.entity.GtfsCalendar;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandlerCalendars;

import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Handler for handling calendar entries so we can filter a PLANit (Service) network and trips with the found GTFS trips for the appropriate day/time period.
 * <p>
 *   Prerequisite: no prerequisites
 * </p>
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerCalendar extends GtfsFileHandlerCalendars {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerCalendar.class.getCanonicalName());

  /** track internal data used to efficiently handle the parsing */
  private final GtfsServicesHandlerData data;

  /** test on each row, when true keep service id, otherwise discard */
  private final Predicate<GtfsCalendar> serviceIdFilter;

  /**
   * Constructor
   *
   * @param gtfsServicesHandlerData      containing all data to track and resources needed to perform the processing
   * @param serviceIdFilter             filters each row whether to register the service id (when true), or not (when false) as active, i.e., its associated trips are eligible
   *                                    for potential parsing
   */
  public GtfsPlanitFileHandlerCalendar(final GtfsServicesHandlerData gtfsServicesHandlerData, Predicate<GtfsCalendar> serviceIdFilter) {
    super();
    this.data = gtfsServicesHandlerData;
    this.serviceIdFilter = serviceIdFilter;
  }

  /**
   * Handle a GTFS calendar row
   */
  @Override
  public void handle(GtfsCalendar gtfsCalendar) {
    var diagnostics = data.getDiagnostics();

    // test would typically be based on what days are deemed eligible
    boolean activeOnChosenDay = serviceIdFilter.test(gtfsCalendar);

    /* a calendar is the day filter rather than something subject to it, so where it stands in time is known the
     * moment it is read and is the only respect it stands in at all. Stated once and handed to both the tally and
     * whatever is registered against it, a calendar not being indexed by id and the two otherwise able to disagree
     * about the same entity */
    var scopeState = GtfsScopeState.unsettledFor(GtfsObjectType.CALENDAR).with(
        GtfsScopeDimension.TEMPORAL, activeOnChosenDay ? GtfsEntityScope.IN : GtfsEntityScope.OUT);
    diagnostics.registerSeen(GtfsObjectType.CALENDAR, null, scopeState);

    if(!activeOnChosenDay){
      diagnostics.registerIssue(
          GtfsParseIssue.CALENDAR_NOT_ACTIVE_ON_DAY, (Enum<?>) null, scopeState, gtfsCalendar.getServiceId());
      return;
    }

    data.registerServiceIdCalendarAsActive(gtfsCalendar);
  }

}
