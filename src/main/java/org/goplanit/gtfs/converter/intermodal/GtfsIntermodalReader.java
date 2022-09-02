package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.converter.intermodal.IntermodalReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderFactory;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderFactory;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Quadruple;
import org.goplanit.zoning.Zoning;

import java.util.logging.Logger;

/**
 * GTFS intermodal reader. Supplements an already populated network and (partially populated) zoning with GTFS services resulting in
 * a consistent service network and routed services PLANit memory model. In case the provided zoning already contains transfer zones
 * an attempt is made to fuse the GTFS stops with existing transfer zones when appliccable, in case this is not possible or such zones
 * are absent altogether they will be injected from scratch.
 * 
 * @author markr
 *
 */
public class GtfsIntermodalReader implements IntermodalReader {
  
  /** intermodal reader settings to use */
  protected final GtfsIntermodalReaderSettings settings;

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesReader.class.getCanonicalName());

  /** id token to use */
  private IdGroupingToken idToken;

  /** Constructor where settings are directly provided such that input information can be extracted from it
   *
   * @param idToken to use for the routed services and service network ids
   * @param settings to use
   */
  protected GtfsIntermodalReader(final IdGroupingToken idToken, final GtfsIntermodalReaderSettings settings){
    this.idToken = idToken;
    this.settings = settings;
  }   

  /**
   * GTFS intermodal reader - when used - only supports reading with services included. Hence, calling this method will log this to the user
   * and will not generate any results
   */
  @Override
  public Pair<MacroscopicNetwork, Zoning> read() throws PlanItException {
    LOGGER.warning("GTFS Intermodal Reader only supports readWithServices(), read() is not supported due to absence of physical network in GTFS data...");
    LOGGER.warning("...To parse a compatible network consider using the OSM Intermodel Reader or another compatible network reader to pre-load the network");
    return null;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
    getSettings().reset();
  }

  /**
   * {@inheritDoc}
   */    
  @Override
  public GtfsIntermodalReaderSettings getSettings() {
    return this.settings;
  }

  /**
   * GTFS intermodal reader supports reading with services.
   * @return true
   */
  @Override
  public boolean supportServiceConversion() {
    return true;
  }

  /**
   * Perform the conversion and parsing into PLANit memory model with service network and services
   *
   * @return created and or further populated network, zoning, service network and services
   */
  @Override
  public Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> readWithServices() {

    /* SERVICES */
    GtfsServicesReader servicesReader = GtfsServicesReaderFactory.create(getSettings().getServiceSettings());
    Pair<ServiceNetwork,RoutedServices> servicesResult = servicesReader.read();

    /* ZONING (PT stops as transfer zones) */
    final var zoningReader = GtfsZoningReaderFactory.create(
        getSettings().getZoningSettings(), getSettings().getReferenceZoning(), servicesResult.first(), servicesResult.second());
    zoningReader.read();

    /* combined result */
    return Quadruple.of(settings.getReferenceNetwork(),settings.getReferenceZoning(),servicesResult.first(),servicesResult.second());
  }
}
