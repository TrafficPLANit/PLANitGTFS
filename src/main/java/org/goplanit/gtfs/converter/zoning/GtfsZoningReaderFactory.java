package org.goplanit.gtfs.converter.zoning;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.zoning.Zoning;

import java.net.URL;
import java.nio.file.Paths;

/**
 * Factory for creating PLANitGTFS zoning (public transport infrastructure) Readers. For now GTFS zoning reader require the presence of a PLANit network with or without transfer zones based on other data sources
 * the GTFS data will be fuxed if present, and supplements the existing data if not yet present.
 * 
 * @author markr
 *
 */
public class GtfsZoningReaderFactory {

  /** Create a PLANitGtfsReader while populating a newly created zoning to populate from scratch
   * 
   * @param settings to use (including the reference network)
   * @return created GTFS reader
   */
  public static GtfsZoningReader create(GtfsPublicTransportReaderSettings settings) {
    PlanItRunTimeException.throwIfNull(settings, "No settings instance provided to GTFS zoning reader factory method");
    PlanItRunTimeException.throwIfNull(settings.getReferenceNetwork(),"Unable to initialise GTFS zoning reader, network not available to base zoning instance from");
    return create(settings, new Zoning(settings.getReferenceNetwork().getIdGroupingToken(),settings.getReferenceNetwork().getNetworkGroupingTokenId()));    
  }  
  
  /** Create a PLANitGtfsReader while providing a zoning to populate (further)
   * 
   * @param settings to use
   * @param zoningToPopulate to populate (further)
   * @return created GTFS reader
   */
  public static GtfsZoningReader create(GtfsPublicTransportReaderSettings settings, Zoning zoningToPopulate) {
    PlanItRunTimeException.throwIfNull(settings, "No settings instance provided to GTFS zoning reader factory method");
    PlanItRunTimeException.throwIfNull(zoningToPopulate, "No zoning instance provided to GTFS zoning reader factory method");
    return new GtfsZoningReader(settings, zoningToPopulate);
  }  

  /** Create a PLANitGtfsReader while providing a zoning to populate further
   * 
   * @param inputSource to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return created GTFS reader
   */
  public static GtfsZoningReader create(String inputSource, String countryName, MacroscopicNetwork referenceNetwork) {
    PlanItRunTimeException.throwIfNull(referenceNetwork, "No reference network provided to GTFS zoning reader factory method");
    return new GtfsZoningReader(
        inputSource, countryName, new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId()),referenceNetwork);
  }    

}
