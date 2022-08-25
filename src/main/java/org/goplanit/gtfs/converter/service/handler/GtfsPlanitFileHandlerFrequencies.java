package org.goplanit.gtfs.converter.service.handler;

import org.goplanit.gtfs.entity.GtfsFrequency;
import org.goplanit.gtfs.handler.GtfsFileHandlerFrequencies;
import org.goplanit.utils.exceptions.PlanItRunTimeException;

import java.util.logging.Logger;

/**
 * Handler for handling GTFS frequencies entries to populate PLANit service (network/routed services) memory model later on
 * <p>
 *   Prerequisite: It is assumed routed services and service network are available and layers are initialised
 * </p>
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerFrequencies extends GtfsFileHandlerFrequencies {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerFrequencies.class.getCanonicalName());

  /** track internal data used to efficiently handle the parsing */
  private final GtfsFileHandlerData data;

  /**
   * Constructor
   *
   * @param gtfsFileHandlerData      containing all data to track and resources needed to perform the processing
   */
  public GtfsPlanitFileHandlerFrequencies(final GtfsFileHandlerData gtfsFileHandlerData) {
    super();
    this.data = gtfsFileHandlerData;

    PlanItRunTimeException.throwIfNull(data.getRoutedServices(), "Routed services not present, unable to parse GTFS frequencies");
    PlanItRunTimeException.throwIfNull(data.getServiceNetwork(), "Services network not present, unable to parse GTFS frequencies");
    // prerequisites
    PlanItRunTimeException.throwIf(data.getRoutedServices().getLayers().isEachLayerEmpty()==true,"No GTFS routes parsed yet, unable to parse GTFS frequencies");
  }

  /**
   * Handle a GTFS frequency entry
   */
  @Override
  public void handle(GtfsFrequency gtfsFrequency) {
    PlanItRunTimeException.throwIf(true, "GTFS frequency handler not yet implemented");
  }

}
