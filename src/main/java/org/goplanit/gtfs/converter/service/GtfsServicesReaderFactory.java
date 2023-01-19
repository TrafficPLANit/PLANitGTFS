package org.goplanit.gtfs.converter.service;

import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.network.MacroscopicNetwork;


/**
 * Factory for creating GtfsRoutedServicesReaders
 * 
 * @author markr
 *
 */
public class GtfsServicesReaderFactory {

  /** Create a GtfsRoutedServicesReader sourced from given input directory
   * 
   * @param inputDirectory to use (directory only, find first compatible file)
   * @param countryName to use
   * @param parentNetwork the network the routed services are assumed to be built upon
   * @param typeChoice to apply
   * @return created routed service reader
   */
  public static GtfsServicesReader create(final String inputDirectory, final String countryName, MacroscopicNetwork parentNetwork, RouteTypeChoice typeChoice) {
    GtfsServicesReader serviceNetworkReader = create(new GtfsServicesReaderSettings(inputDirectory, countryName, parentNetwork, typeChoice));
    return serviceNetworkReader;
  }  
  
  /** Create a PlanitRoutedServicesReader based on given settings which in turn contain information on location and parent network to use
   * 
   * @param settings to use
   * @return created routed service reader
   */
  public static GtfsServicesReader create(final GtfsServicesReaderSettings settings) {
    return new GtfsServicesReader(settings.getReferenceNetwork().getIdGroupingToken(), settings);
  }   
}
