package org.goplanit.gtfs.converter.intermodal;

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

import java.util.function.Function;

/**
 * Factory class for creating intermodal reader for GTFS files
 * 
 * @author markr
 *
 */
public class GtfsIntermodalReaderFactory {

  /** Create a GtfsIntermodalReader sourced from given input directory
   *
   * @param inputDirectory to use (directory only, find first compatible file)
   * @param countryName to use
   * @param parentNetwork the network the routed services and service network are to be built upon
   * @param parentZoning the zoning the transfer zones are to be expanded upon or mapped to
   * @param typeChoice to apply, this pertains to how the GTFS siles are to be parsed as they have different specifications
   * @return created routed service reader
   */
  public static GtfsIntermodalReader create(final String inputDirectory, final String countryName, MacroscopicNetwork parentNetwork, Zoning parentZoning, RouteTypeChoice typeChoice) {

    var intermodalReaderSettings =
        new GtfsIntermodalReaderSettings(
            inputDirectory, countryName, parentNetwork, parentZoning, typeChoice);
    return create(intermodalReaderSettings);
  }

  /** Create a GtfsIntermodalReader based on given settings which in turn contain information on required location and reference inputs
   *
   * @param settings to use
   * @return created routed service reader
   */
  public static GtfsIntermodalReader create(final GtfsIntermodalReaderSettings settings) {
    return new GtfsIntermodalReader(settings.getReferenceNetwork().getIdGroupingToken(), settings);
  }
}
