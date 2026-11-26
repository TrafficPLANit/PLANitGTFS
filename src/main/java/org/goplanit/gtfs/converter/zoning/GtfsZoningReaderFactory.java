package org.goplanit.gtfs.converter.zoning;

import com.sun.istack.NotNull;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.zoning.Zoning;

import java.util.function.Function;

/**
 * Factory for creating PLANitGTFS zoning (public transport infrastructure) readers. GTFS zoning data, e.g. stops
 * converted to PLANit transfer zones, require the service network and routed services (GTFS services, trips) to have
 * been parsed already via the {@link org.goplanit.gtfs.converter.service.GtfsServicesReader}, and the input source to
 * be consistent with that parse. End-users are likely best served by parsing everything in one go with the
 * {@link org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReader} instead of this factory.
 * 
 * @author markr
 *
 */
public class GtfsZoningReaderFactory {

  /**
   * Create a GTFS zoning reader that uses an already present service network and routed services to improve the
   * parsing of PT stops, i.e., transfer zones: the mode of the routed services, trips and their stops is used to match
   * the stops already present in the zoning. Example: the network is sourced from Open Street Map, parsing only the
   * physical network and stops, to be complemented with GTFS services. Matching OSM stops (transfer zones) to GTFS
   * stops requires the mode, which comes from the GTFS services parsed earlier, held in the service network and routed
   * services, while the OSM stops are part of the zoning. In that case this factory method is the best choice to
   * combine the two.
   *
   * @param settings                       to use, containing the physical reference network and reference to source
   *                                       file and other configuration settings
   * @param zoningToPopulate               the zoning to populate further beyond the already partially populated
   *                                       transfer zones
   * @param serviceNetwork                 the compatible PLANit service network that is assumed to have been
   *                                       constructed from the same GTFS source files as this zoning reader will use
   * @param routedServices                 the compatible PLANit routed services that is assumed to have been
   *                                       constructed from the same GTFS source files as this zoning reader will use
   * @param serviceNodeToGtfsStopIdMapping allows mapping between the service nodes in the routed services and the
   *                                       underlying GTFS stop ids which are presumed present such that we can create
   *                                       routes on the physical road/rail network
   * @return zoning reader to use for parsing
   */
  public static GtfsZoningReader create(
      @NotNull GtfsZoningReaderSettings settings,
      @NotNull Zoning zoningToPopulate,
      @NotNull ServiceNetwork serviceNetwork,
      @NotNull RoutedServices routedServices,
      @NotNull Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping) {
    return new GtfsZoningReader(settings, zoningToPopulate, serviceNetwork, routedServices);
  }

  /**
   * Create a zoning reader that forms one stage of a wider parse, and therefore leaves reporting what became of the
   * feed to the caller driving it, which can only account for it once every stage has run
   *
   * @param settings                       to use, containing the physical reference network and reference to source
   *                                       file and other configuration settings
   * @param zoningToPopulate               the zoning to populate further beyond the already partially populated
   *                                       transfer zones
   * @param serviceNetwork                 the compatible PLANit service network that is assumed to have been
   *                                       constructed from the same GTFS source files as this zoning reader will use
   * @param routedServices                 the compatible PLANit routed services that is assumed to have been
   *                                       constructed from the same GTFS source files as this zoning reader will use
   * @param serviceNodeToGtfsStopIdMapping allows mapping between the service nodes in the routed services and the
   *                                       underlying GTFS stop ids which are presumed present such that we can create
   *                                       routes on the physical road/rail network
   * @return zoning reader to use for parsing
   */
  public static GtfsZoningReader createWithoutCoverageReport(
      @NotNull GtfsZoningReaderSettings settings,
      @NotNull Zoning zoningToPopulate,
      @NotNull ServiceNetwork serviceNetwork,
      @NotNull RoutedServices routedServices,
      @NotNull Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping) {
    var reader = create(
        settings, zoningToPopulate, serviceNetwork, routedServices, serviceNodeToGtfsStopIdMapping);
    reader.suppressCoverageReport();
    return reader;
  }
}
