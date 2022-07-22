package org.goplanit.gtfs.converter.service;

import org.goplanit.converter.MultiConverterReader;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.*;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.Pair;

import java.util.logging.Logger;

/**
 * Implementation of a GTFS services reader from GTFS files. This reads the following GTFS files:
 *  * <ul>
 *  *   <li>routes.txt</li>
 *  * </ul>
 *  The result is both the service network as well as the routed services as a PLANit memory model
 *
 * @author markr
 *
 */
public class GtfsServicesReader implements MultiConverterReader<ServiceNetwork, RoutedServices> {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesReader.class.getCanonicalName());
  
  /** the settings for this reader */
  private final GtfsServicesReaderSettings settings;

  /** the routed services to populate */
  private final RoutedServices routedServices;

  /** the service network to populate */
  private final ServiceNetwork serviceNetwork;

  /** Constructor where settings are directly provided such that input information can be extracted from it
   *
   * @param settings to use
   */
  protected GtfsServicesReader(final GtfsServicesReaderSettings settings) {
    this(IdGroupingToken.collectGlobalToken(), settings);
  }

  /** Constructor where settings are directly provided such that input information can be extracted from it
   * 
   * @param idToken to use for the routed services and service network ids
   * @param settings to use
   */
  protected GtfsServicesReader(final IdGroupingToken idToken, final GtfsServicesReaderSettings settings) {
    this.settings = settings;
    this.serviceNetwork = new ServiceNetwork(idToken, settings.getReferenceNetwork());
    this.routedServices = new RoutedServices(idToken, serviceNetwork);
  }  

  /**
   * {@inheritDoc}
   */
  @Override
  public Pair<ServiceNetwork, RoutedServices> read(){

    try {
      
      //TODO
      
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException(String.format("Error while populating routed services %s in PLANitIO", routedServices.getXmlId()),e);
    }    
    
    return Pair.of(null,null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GtfsServicesReaderSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getTypeDescription() {
    return "GTFS services Reader";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    settings.reset();
  }

}
