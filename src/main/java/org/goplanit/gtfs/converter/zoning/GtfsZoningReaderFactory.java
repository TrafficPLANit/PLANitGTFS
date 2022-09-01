package org.goplanit.gtfs.converter.zoning;

import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.zoning.Zoning;

import java.util.function.Function;

/**
 * Factory for creating PLANitGTFS zoning (public transport infrastructure) Readers. GTFS zoning data, e.g. stops converted to PLANit transfer zone, require the
 * service network and routed services (GTFS services, trips) to already been parsed via the {@link org.goplanit.gtfs.converter.service.GtfsServicesReader}. It is also
 * expected the input source is consistent with that parsing exercise. As an end-user it is likely best to directly parse all in one integrated go using the {@link org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReader}
 * instead of this factory.
 * 
 * @author markr
 *
 */
public class GtfsZoningReaderFactory {

  /**
   * Create a GTFS zoning reader, where information from an already present service network and routed services is leveraged to improve
   * the quality of the parsing of PT stops, i.e., transfer zones. Here, the mode of routed services, trips, and their stops is used to
   * match the stops already present in the zoning. Example: when sourcing the network from Open Street Map and only the phsyical network and stops
   * are parsed. These can then be complemented with GTFS services. In order to properly match OSM stops (transfer zones) to GTFS stops, the mode is required.
   * This mode is obtained from the GTFS services parsed earlier and available in the Service network and Routed Services, while the OSM stops are part of
   * the Zoning. In that case this factory method is the best choice to fuse the two together.
   *
   * @param settings                 to use, containing the physical reference network and reference to source file and other configuration settings
   * @param zoningToPopulate         the zoning to populate further beyond the already partially populated transfer zones
   * @param serviceNetwork           the compatible PLANit service network that is assumed to have been constructed from the same GTFS source files as this zoning reader will use
   * @param routedServices           the compatible PLANit routed services that is assumed to have been constructed from the same GTFS source files as this zoning reader will use
   * @return zoning reader to use for parsing
   */
  public static GtfsZoningReader create(
      GtfsZoningReaderSettings settings,
      Zoning zoningToPopulate,
      ServiceNetwork serviceNetwork,
      RoutedServices routedServices) {
    return new GtfsZoningReader(settings, zoningToPopulate, serviceNetwork, routedServices);
  }
}
