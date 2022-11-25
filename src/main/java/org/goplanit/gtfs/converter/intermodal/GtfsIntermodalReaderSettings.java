package org.goplanit.gtfs.converter.intermodal;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.cost.physical.PhysicalCost;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.io.converter.network.PlanitNetworkReaderSettings;
import org.goplanit.io.converter.zoning.PlanitZoningReaderSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.ManagedId;
import org.goplanit.utils.id.ManagedIdEntityFactory;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.zoning.Zoning;

import java.util.function.Function;

/**
 * Settings of GtfsIntermodalReader
 * 
 * @author markr
 *
 */
public class GtfsIntermodalReaderSettings implements ConverterReaderSettings {

  /** default search for chepeast paths is based on free flow approach */
  public final String DEFAULT_STOP_TO_STOP_COST_APPROACH = PhysicalCost.FREEFLOW;
  
  /** the services settings to use */
  protected final GtfsServicesReaderSettings servicesReaderSettings;
  
  /** the zoning settings to use */
  protected final GtfsZoningReaderSettings zoningSettings;

  private final String stopToStopPathSearchPhysicalCostApproach = DEFAULT_STOP_TO_STOP_COST_APPROACH;

  /** Zoning to populate (further) */
  protected final Zoning zoningtoPopulate;

  /** Constructor with user defined source locale
   *
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param routeTypeChoice to apply
   * @param parentNetwork to use
   */
  public GtfsIntermodalReaderSettings(String inputSource, String countryName, final MacroscopicNetwork parentNetwork, Zoning zoningtoPopulate, RouteTypeChoice routeTypeChoice) {
    this.servicesReaderSettings = new GtfsServicesReaderSettings(inputSource, countryName, parentNetwork, routeTypeChoice);
    this.zoningSettings = new GtfsZoningReaderSettings(servicesReaderSettings);
    this.zoningtoPopulate = zoningtoPopulate;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    getServiceSettings().reset();
    getZoningSettings().reset();
  }   
  
  /** provide access to the service reader settings
   * @return network reader settings
   */
  public GtfsServicesReaderSettings getServiceSettings() {
    return servicesReaderSettings;
  }
  
  /** provide access to the zoning reader settings
   * @return zoning reader settings
   */
  public GtfsZoningReaderSettings getZoningSettings() {
    return zoningSettings;
  }

  /**
   * Collect reference physical network used
   *
   * @return reference network
   */
  public MacroscopicNetwork getReferenceNetwork() {
    return servicesReaderSettings.getReferenceNetwork();
  }

  /**
   * Collect reference zoning used (to populate further)
   *
   * @return reference zoning
   */
  public Zoning getReferenceZoning() {
    // todo maybe should not be part of settings, but on reader itself, like zoning reader...
    return zoningtoPopulate;
  }

  /** The methodology used to find the paths between stops by means of its full canonical class name which is assumed to be supported by
   * PLANit as a valid cost generating method
   *
   * @return stopToStopPathSearchPhysicalCostApproach*/
  public String getStopToStopPathSearchPhysicalCostApproach() {
    return stopToStopPathSearchPhysicalCostApproach;
  }
}
