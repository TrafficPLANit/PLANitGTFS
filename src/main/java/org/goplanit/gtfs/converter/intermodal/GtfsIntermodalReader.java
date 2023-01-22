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

    /* SERVICES without geo filter (since locations are currently only parsed when considering GTFS stops via (transfer) zoning reader), hence
    *  all routes/services are initially mapped to PLANit equivalents but without mapping to physical network yet*/
    GtfsServicesReader servicesReader = GtfsServicesReaderFactory.create(getSettings().getServiceSettings());
    Pair<ServiceNetwork,RoutedServices> servicesResult = servicesReader.read();

    /* ZONING (PT stops as transfer zones) this parses GTFS stops and their locations, parsed GTFS stops are constrained to bounding box of underlying physical network*/
    final var zoningReader = GtfsZoningReaderFactory.create(
        getSettings().getZoningSettings(),
        getSettings().getReferenceZoning(),
        servicesResult.first(),
        servicesResult.second(),
        servicesReader.getServiceNodeToGtfsStopIdMapping());
    var zoning = zoningReader.read();

    /* INTEGRATE: integrate the zoning, service network and network by finding paths between the identified stops for all given services,
    * for now, we generate the paths based on simple Dijkstra shortest paths, in the future more sophisticated alternatives could be used */
    var integrator = new GtfsServicesAndZoningReaderIntegrator(
        settings,
        zoning,
        servicesResult.first(),
        servicesResult.second(),
        servicesReader.getServiceNodeToGtfsStopIdMapping(),
        zoningReader.getGtfsStopIdToTransferZoneMapping());
    integrator.execute();

    /* CLEAN-UP: remove all routes/services that fall outside the physical network's bounding box, i.e., remained unmapped */
    servicesResult.first().getTransportLayers().forEach( l -> l.getLayerModifier().removeUnmappedServiceNetworkEntities());
    servicesResult.second().getLayers().forEach( l -> l.getLayerModifier().truncateToServiceNetwork());

    /* combined result */
    return Quadruple.of(settings.getReferenceNetwork(),zoning,servicesResult.first(),servicesResult.second());
  }
}
