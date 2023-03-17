package org.goplanit.gtfs.converter.intermodal;

import com.sun.istack.NotNull;
import org.goplanit.gtfs.converter.service.GtfsServicesReader;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.xml.generated.XMLElementMacroscopicNetwork;
import org.goplanit.xml.generated.XMLElementMacroscopicZoning;
import org.goplanit.zoning.Zoning;

import java.time.DayOfWeek;
import java.util.function.Function;

/**
 * Factory class for creating intermodal reader for GTFS files
 * 
 * @author markr
 *
 */
public class GtfsIntermodalReaderFactory {

  /** Create a GtfsIntermodalReader sourced from given input directory, but without a zoning available yet, an empty zoning based on global id token will be created
   *
   * @param inputDirectory to use (directory only, find first compatible file)
   * @param countryName to use
   * @param dayOfWeek dayOfWeek to filter on
   * @param parentNetwork the network the routed services and service network are to be built upon
   * @param typeChoice to apply, this pertains to how the GTFS files are to be parsed as they have different specifications
   * @return created routed service reader
   */
  public static GtfsIntermodalReader create(final String inputDirectory, final String countryName, @NotNull DayOfWeek dayOfWeek, @NotNull MacroscopicNetwork parentNetwork,RouteTypeChoice typeChoice) {
    return create(inputDirectory, countryName, dayOfWeek, parentNetwork, IdGroupingToken.collectGlobalToken(), typeChoice);
  }

  /** Create a GtfsIntermodalReader sourced from given input directory, but without a zoning available yet, an empty zoning based on the id grouping token will be created for you
   *
   * @param inputDirectory to use (directory only, find first compatible file)
   * @param countryName to use
   * @param dayOfWeek dayOfWeek to filter on
   * @param parentNetwork the network the routed services and service network are to be built upon
   * @param zoningIdToken the zoning id token to use for creating a new empty zoning to map the Gtfs stops to (transfer zones)
   * @param typeChoice to apply, this pertains to how the GTFS files are to be parsed as they have different specifications
   * @return created routed service reader
   */
  public static GtfsIntermodalReader create(
      final String inputDirectory, final String countryName, @NotNull DayOfWeek dayOfWeek, @NotNull MacroscopicNetwork parentNetwork, @NotNull IdGroupingToken zoningIdToken, RouteTypeChoice typeChoice) {
    var emptyZoning = new Zoning(zoningIdToken, parentNetwork.getNetworkGroupingTokenId());
    return create(inputDirectory, countryName, dayOfWeek, parentNetwork, emptyZoning, typeChoice);
  }

  /** Create a GtfsIntermodalReader sourced from given input directory.
   *
   * @param inputDirectory to use (directory only, find first compatible file)
   * @param countryName to use
   * @param parentNetwork the network the routed services and service network are to be built upon
   * @param parentZoning the zoning the transfer zones are to be expanded upon or mapped to
   * @param typeChoice to apply, this pertains to how the GTFS files are to be parsed as they have different specifications
   * @return created routed service reader
   */
  public static GtfsIntermodalReader create(final String inputDirectory, final String countryName, MacroscopicNetwork parentNetwork, Zoning parentZoning, RouteTypeChoice typeChoice) {
    var intermodalReaderSettings = new GtfsIntermodalReaderSettings(inputDirectory, countryName, typeChoice);
    return create(parentNetwork, parentZoning, intermodalReaderSettings);
  }

  /** Create a GtfsIntermodalReader sourced from given input directory
   *
   * @param inputDirectory to use (directory only, find first compatible file)
   * @param countryName to use
   * @param dayOfWeek dayOfWeek to filter on
   * @param parentNetwork the network the routed services and service network are to be built upon
   * @param parentZoning the zoning the transfer zones are to be expanded upon or mapped to
   * @param typeChoice to apply, this pertains to how the GTFS siles are to be parsed as they have different specifications
   * @return created routed service reader
   */
  public static GtfsIntermodalReader create(
      final String inputDirectory, final String countryName, DayOfWeek dayOfWeek, final MacroscopicNetwork parentNetwork, final Zoning parentZoning, RouteTypeChoice typeChoice) {
    return create(parentNetwork, parentZoning, new GtfsIntermodalReaderSettings(inputDirectory, countryName, dayOfWeek, typeChoice));
  }

  /** Create a GtfsIntermodalReader based on given settings which in turn contain information on required location and reference inputs
   *
   * @param settings to use
   * @return created routed service reader
   */
  public static GtfsIntermodalReader create(final MacroscopicNetwork parentNetwork, final Zoning parentZoning, final GtfsIntermodalReaderSettings settings) {
    return new GtfsIntermodalReader(parentNetwork.getIdGroupingToken(), parentNetwork, parentZoning, settings);
  }
}
