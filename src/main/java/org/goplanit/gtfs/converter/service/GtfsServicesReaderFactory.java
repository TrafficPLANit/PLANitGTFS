package org.goplanit.gtfs.converter.service;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.IdGroupingToken;


/**
 * Factory for creating GtfsRoutedServicesReaders
 * 
 * @author markr
 *
 */
public class GtfsServicesReaderFactory {

  /** Create a GtfsRoutedServicesReader based on custom id token
   * 
   * @param idToken to use for routed services id generation
   * @param parentNetwork the parent network the services are assumed to be built upon
   * @return created routed service reader
   */
  public static GtfsServicesReader create(final IdGroupingToken idToken, MacroscopicNetwork parentNetwork) {
    return new GtfsServicesReader(idToken, new GtfsServicesReaderSettings(parentNetwork));
  }
  
  /** Create a GtfsRoutedServicesReader sourced from given input directory
   * 
   * @param inputDirectory to use (directory only, find first compatible file)
   * @param parentNetwork the network the routed services are assumed to be built upon  
   * @return created routed service reader
   */
  public static GtfsServicesReader create(final String inputDirectory, MacroscopicNetwork parentNetwork) {
    GtfsServicesReader serviceNetworkReader = create(IdGroupingToken.collectGlobalToken(), parentNetwork);
    serviceNetworkReader.getSettings().setInputDirectory(inputDirectory);
    return serviceNetworkReader;
  }  
  
  /** Create a PlanitRoutedServicesReader based on given settings which in turn contain information on location and parent network to use
   * 
   * @param settings to use
   * @return created routed service reader
   */
  public static GtfsServicesReader create(final GtfsServicesReaderSettings settings) {
    return new GtfsServicesReader(IdGroupingToken.collectGlobalToken(), settings);
  }   
}
