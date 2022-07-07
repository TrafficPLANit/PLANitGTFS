package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.converter.zoning.GtfsPublicTransportReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.referencing.operation.MathTransform;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Handler for handling stops and augmenting a PLANit zoning with the found stops in the process
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerStops extends GtfsFileHandlerStops {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerStops.class.getCanonicalName());

  /** profiler to use */
  private final GtfsZoningHandlerProfiler profiler;

  /** track existing transfer zones present geo spatially to be able to fuse with GTFS data when appropriate */
  private Quadtree existingTransferZones;

  /** geo tools with CRS based configuration to apply */
  private PlanitJtsCrsUtils geoTools;

  /** apply this transformation to all coordinates so they are consistent with the underlying PLANit entities */
  private MathTransform crsTransform;

  /** zoning to populate */
  private final Zoning zoning;

  /** settings containing configuration */
  private final GtfsPublicTransportReaderSettings settings;

  private void initialise(){
    this.existingTransferZones = GeoContainerUtils.toGeoIndexed(zoning.getTransferZones());
    this.geoTools = new PlanitJtsCrsUtils(settings.getReferenceNetwork().getCoordinateReferenceSystem());
    this.crsTransform = PlanitJtsUtils.findMathTransform(PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS, geoTools.getCoordinateReferenceSystem());
  }

  /**
   * Find nearby zones based on a given search radius
   * @param location point location to search around
   * @param pointSearchRadiusMeters search radius to apply
   * @return found transfer zones around this location
   */
  private Collection<TransferZone> findNearbyTransferZones(Point location, double pointSearchRadiusMeters) {
    var searchEnvelope = geoTools.createBoundingBox(location.getX(),location.getY(),pointSearchRadiusMeters);
    return GeoContainerUtils.queryZoneQuadtree(this.existingTransferZones, searchEnvelope);
  }

  /**
   * Constructor
   *
   * @param zoningToPopulate the PLANit zoning instance to populate (further)
   * @param settings to apply where needed
   * @param profiler to use
   */
  public GtfsPlanitFileHandlerStops(final Zoning zoningToPopulate, final GtfsPublicTransportReaderSettings settings, final GtfsZoningHandlerProfiler profiler) {
    super();
    this.profiler = profiler;
    this.zoning = zoningToPopulate;
    this.settings = settings;
    initialise();
  }

  private void handleStopPlatform(GtfsStop gtfsStop) {
    Point gtfsStopLocation = null;
    try {
      gtfsStopLocation = (Point) PlanitJtsUtils.transformGeometry(PlanitJtsUtils.createPoint(gtfsStop.getLocationAsCoord()), this.crsTransform);
    }catch(Exception e){
      LOGGER.severe(String.format("Unable to transform geometry of GTFS stop %s to PLANit network CRS", gtfsStop.getStopId()));
    }

    //todo: the creation of the bounding box around a point expects lat/long not converted CRS....figure out how to make it better or
    // otherwise pass in long/lat instead
    Collection<TransferZone> nearbyTransferZones = findNearbyTransferZones(gtfsStopLocation, settings.getGtfsStopToTransferZoneSearchRadiusMeters());
    if(!nearbyTransferZones.isEmpty()){
      int bla = 4;
    }
    //todo: continue here --> make sure we query somewhat around the node
  }

  /**
   * Handle a GTFS stop
   */
  @Override
  public void handle(GtfsStop gtfsStop) {
    switch (gtfsStop.getLocationType()){
      case STOP_PLATFORM:
        handleStopPlatform(gtfsStop);
      case BOARDING_AREA:
        // not processed yet
        return;
      case STATION:
        // not processed yet
        return;
      case GENERIC_NODE:
        // not processed yet
        return;
      case ENTRANCE_EXIT:
        // not processed yet
        return;
      default:
        throw new PlanItRunTimeException("Unrecognised GTFS stop location type %s encountered", gtfsStop.getLocationType());
    }
  }

}
