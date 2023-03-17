package org.goplanit.gtfs.converter.service;

import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.network.MacroscopicNetwork;

import java.time.DayOfWeek;


/**
 * Factory for creating GtfsRoutedServicesReaders
 * 
 * @author markr
 *
 */
public class GtfsServicesReaderFactory {

  /** Create a GtfsRoutedServicesReader sourced from given input directory
   *
   * @param parentNetwork the network the routed services are assumed to be built upon
   * @param inputDirectory to use (directory only, find first compatible file)
   * @param countryName to use
   * @param dayOfWeek to filter on
   * @param typeChoice to apply
   * @return created routed service reader
   */
  public static GtfsServicesReader create(MacroscopicNetwork parentNetwork, final String inputDirectory, final String countryName, DayOfWeek dayOfWeek, RouteTypeChoice typeChoice) {
    GtfsServicesReader serviceNetworkReader = create(parentNetwork, new GtfsServicesReaderSettings(inputDirectory, countryName, dayOfWeek, typeChoice));
    return serviceNetworkReader;
  }

  /** Create a PlanitRoutedServicesReader based on given settings which in turn contain information on location and parent network to use
   *
   * @param parentNetwork the network the routed services are assumed to be built upon
   * @param settings to use
   * @return created routed service reader
   */
  public static GtfsServicesReader create(MacroscopicNetwork parentNetwork, final GtfsServicesReaderSettings settings) {
    return new GtfsServicesReader(parentNetwork.getIdGroupingToken(), parentNetwork, settings);
  }   
}
