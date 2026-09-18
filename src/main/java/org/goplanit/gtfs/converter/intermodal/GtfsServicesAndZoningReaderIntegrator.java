package org.goplanit.gtfs.converter.intermodal;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.goplanit.component.PlanitComponentFactory;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.gtfs.converter.GtfsConverterModeMappingData;
import org.goplanit.gtfs.converter.diagnostics.GtfsPlanitEntityDiagnostics;
import org.goplanit.gtfs.parallel.AStarBatchExecutionData;
import org.goplanit.gtfs.parallel.AStarPtLegSegmentBatchExecutorService;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.utils.geo.PlanitCrsUtils;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.network.layer.service.ServiceNode;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.zoning.Zoning;

import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Integrates the service network and routed services (GTFS itinerary) with the physical road network and zoning
 * (GTFS stop based transfer zones).
 *
 * @author markr
 */
public class GtfsServicesAndZoningReaderIntegrator {

  /** Logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsServicesAndZoningReaderIntegrator.class.getCanonicalName());

  private final GtfsIntermodalReaderSettings settings;
  private final Zoning zoning;
  private final ServiceNetwork serviceNetwork;
  private final RoutedServices routedServices;

  private final Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping;

  private final Function<String, TransferZone> gtfsStopIdToTransferZoneMapping;

  /** profiler tracking what became of each service leg segment during integration */
  private final GtfsIntegrationProfiler profiler;

  /**
   * Initialise indices, data and thread local A* shortest path algos that are to be used and wrap them into a
   * dedicated data instance
   *
   * @return data instance to be provided to each thread
   */
  private AStarBatchExecutionData createBatchData(){

    /* determine eligible service modes by intersecting physical layer modes with activated public transport modes
     * of the GTFS settings */
    var modeMappingData = new GtfsConverterModeMappingData(serviceNetwork, settings.getServiceSettings());
    var eligibleServiceModes = serviceNetwork.getTransportLayers().getSupportedModes();
    eligibleServiceModes.retainAll(modeMappingData.getActivatedPlanitModes());
    if(eligibleServiceModes.isEmpty()){
      LOGGER.severe("No eligible modes found on any of the service network layers that are configured as " +
              "activated for the GTFS reader, consider revising your configuration");
    }

    // constant and fixed across all threads, so cache once and reuse
    var physicalCostApproach =
            PlanitComponentFactory.createAndDispatch(
                    AbstractPhysicalCost.class,
                    settings.getStopToStopPathSearchPhysicalCostApproach(),
                    new Object[]{ serviceNetwork.getParentNetwork().getIdGroupingToken()});

    return new AStarBatchExecutionData(
            serviceNetwork,
            routedServices,
            zoning,
            modeMappingData,
            serviceNodeToGtfsStopIdMapping,
            gtfsStopIdToTransferZoneMapping,
            profiler,
            physicalCostApproach,
            eligibleServiceModes);
  }

  /**
   * Validate inputs to see if integration and creation of physical paths and relating them to the service network
   * is supported
   */
  private void validateInputs() {
    PlanItRunTimeException.throwIfNull(this.serviceNodeToGtfsStopIdMapping,
            "serviceNodeToGtfsStopIdMapping is null");
    PlanItRunTimeException.throwIfNull(this.gtfsStopIdToTransferZoneMapping,
            "gtfsStopIdToTransferZoneMapping is null");
    PlanItRunTimeException.throwIfNull(this.serviceNetwork, "serviceNetwork is null");
    PlanItRunTimeException.throwIfNull(this.settings, "GTFS Intermodal reader settings is null");
    PlanItRunTimeException.throwIfNull(this.zoning, "zoning is null");

    //todo: multiple layers should be possible to implement but at this point simply has not been done due to
    // absence of a case where this is used
    PlanItRunTimeException.throwIf(this.serviceNetwork.getParentNetwork().getTransportLayers().size()>1,
            "Currently GTFS converter only supports physical reference networks with a single layer");
    PlanItRunTimeException.throwIf(this.serviceNetwork.getTransportLayers().size()>1,
            "Currently GTFS converter only supports service networks with a single layer");
  }


  /**
   * Make sure that all relevant geometries are in a linear projected CRS so A* shortest path is computationally
   * more efficient
   *
   * @return original CRS to revert to after done
   */
  private CoordinateReferenceSystem initialiseLinearCrsTransformation() {
    var physicalNetwork = serviceNetwork.getParentNetwork();
    // routed service have no geographic information of themselves and can be ignored
    // serviceNetwork utilises underlying physical network for its geometries but has no geometry of its own either
    if(!physicalNetwork.getCoordinateReferenceSystem().equals(zoning.getCoordinateReferenceSystem())){
      throw new PlanItRunTimeException("Expect zoning and network to have the same coordinate reference system");
    }
    var originalCrs = physicalNetwork.getCoordinateReferenceSystem();
    if(PlanitCrsUtils.isLinearCRSWithLengthCompatibleUnit(originalCrs)){
      return originalCrs;
    }

    String desiredEpsg = PlanitCrsUtils.findProjectedCrsEpsgCodeByCountryName(
            settings.getCountryName(), true /* use fallback web mercator */);
    var destinationCrs = PlanitCrsUtils.createCoordinateReferenceSystem(desiredEpsg);
    LOGGER.info(String.format(
            "Temporarily converting CRS (%s) to linear equivalent (%s) for optimised shortest path calculation " +
                    "performance", originalCrs.getName(), destinationCrs.getName()));
    physicalNetwork.transform(destinationCrs);
    zoning.transform(destinationCrs);

    return originalCrs;
  }

  /**
   * Revert back to original CRS post-shortest path calculations to reinstate original geometries
   *
   * @param originalCrs to revert back to
   */
  private void revertLinearCrsTransformationTo(CoordinateReferenceSystem originalCrs) {
    // if already correct, we did not transform, we do not need to transform back
    if(PlanitCrsUtils.isLinearCRSWithLengthCompatibleUnit(originalCrs)){
      return;
    }
    var network = serviceNetwork.getParentNetwork();
    LOGGER.info(String.format(
            "Converting linear projected CRS (%s) back to original (%s) after shortest path calculation is complete",
            network.getCoordinateReferenceSystem().getName(), originalCrs.getName()));
    serviceNetwork.getParentNetwork().transform(originalCrs);
    zoning.transform(originalCrs);
  }

  /**
   * Constructor
   *
   * @param settings of the parent reader used
   * @param zoning to integrate
   * @param routedServices to integrate
   * @param serviceNetwork to integrate
   * @param serviceNodeToGtfsStopIdMapping mapping from PLANit service nodes to GTFS stop ids
   * @param gtfsStopIdToTransferZoneMapping mapping from GTFS stop id to PLANit transfer zone
   */
  public GtfsServicesAndZoningReaderIntegrator(
      GtfsIntermodalReaderSettings settings,
      Zoning zoning,
      ServiceNetwork serviceNetwork,
      RoutedServices routedServices,
      Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping,
      Function<String, TransferZone> gtfsStopIdToTransferZoneMapping) {
    this(settings, zoning, serviceNetwork, routedServices, serviceNodeToGtfsStopIdMapping,
        gtfsStopIdToTransferZoneMapping,
        new GtfsIntegrationProfiler(
            settings.getDiagnosticsRetentionLimit(), settings.getDiagnosticsSampleSize()));
  }

  /**
   * Constructor allowing the caller to supply the profiler, so the diagnostics it records into can be combined with
   * those of the preceding stages
   *
   * @param settings of the parent reader used
   * @param zoning to integrate
   * @param routedServices to integrate
   * @param serviceNetwork to integrate
   * @param serviceNodeToGtfsStopIdMapping mapping from PLANit service nodes to GTFS stop ids
   * @param gtfsStopIdToTransferZoneMapping mapping from GTFS stop id to PLANit transfer zone
   * @param profiler to record into
   */
  public GtfsServicesAndZoningReaderIntegrator(
      GtfsIntermodalReaderSettings settings,
      Zoning zoning,
      ServiceNetwork serviceNetwork,
      RoutedServices routedServices,
      Function<ServiceNode, String> serviceNodeToGtfsStopIdMapping,
      Function<String, TransferZone> gtfsStopIdToTransferZoneMapping,
      GtfsIntegrationProfiler profiler) {

    this.profiler = profiler;

    this.serviceNodeToGtfsStopIdMapping = serviceNodeToGtfsStopIdMapping;
    this.gtfsStopIdToTransferZoneMapping = gtfsStopIdToTransferZoneMapping;

    this.settings = settings;
    this.zoning = zoning;
    this.serviceNetwork = serviceNetwork;
    this.routedServices = routedServices;

    validateInputs();
  }

  /**
   * Collect what became of the PLANit entities this integration set out to build, so a caller reporting on the parse
   * as a whole can account for them alongside the stages that preceded it
   *
   * @return PLANit entity diagnostics
   */
  public GtfsPlanitEntityDiagnostics getPlanitEntityDiagnostics() {
    return profiler.getPlanitEntityDiagnostics();
  }

  /**
   * Perform the integration where we identify paths between each of the used GTFS stop service nodes on the
   * physical road network and update the PLANit references in the service legs accordingly
   */
  public void execute() {
    // we'll be doing thousands of A* shortest path calcs to map the leg segments between stops. To optimise
    // calculation of heuristic distances within the algorithm the CRS should be one that is linear in lengths to avoid
    // costly calcs. Hence, we temporarily transform ALL geometries to such a CRS based on the destination country
    // and then transform back afterward to avoid affecting state
    var originalCrs = initialiseLinearCrsTransformation();

    // To further speed this up, we run this in parallel. To do so, we create batches of shortest path calcs each
    // dispatched to the first available thread all encapsulated within the below executor service
    var executor = AStarPtLegSegmentBatchExecutorService.create(createBatchData());
    try{
      executor.execute();
    }catch (Exception e){
      LOGGER.severe("Something went wrong in creating shortest paths between service leg segments");
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
    }

    /* progress is reported at doubling thresholds only, so the final total requires its own entry. What could not be
     * mapped is not reported here, the reader that drives this integration accounting for it alongside the other
     * stages once they have all run */
    profiler.logProgress();

    if(!PlanitCrsUtils.isLinearCRSWithLengthCompatibleUnit(originalCrs)){
      revertLinearCrsTransformationTo(originalCrs);
    }
  }

  /**
   * Collect the profiler tracking the integration
   *
   * @return profiler
   */
  public GtfsIntegrationProfiler getProfiler(){
    return profiler;
  }

  /**
   * Reset internal (temporary) state
   */
  public void reset(){
    profiler.reset();
  }
}
