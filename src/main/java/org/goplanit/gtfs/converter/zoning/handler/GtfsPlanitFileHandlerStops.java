package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.*;
import org.goplanit.utils.graph.Edge;
import org.goplanit.utils.misc.CharacterUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.zoning.TransferZone;
import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.goplanit.utils.locale.DrivingDirectionDefaultByCountry.isLeftHandDrive;

/**
 * Handler for handling stops and augmenting a PLANit zoning with the found stops in the process
 * 
 * @author markr
 *
 */
public class GtfsPlanitFileHandlerStops extends GtfsFileHandlerStops {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsPlanitFileHandlerStops.class.getCanonicalName());

  /** data tracking during parsing */
  private final GtfsZoningHandlerData data;

  /** extract last entry from Transfer zone external id based on comma separation
  * @param transferZone to use
   * @return last entry or empty string
  */
  private String getLastTransferZoneExternalId(TransferZone transferZone){
    var splitExternalId = transferZone.getSplitExternalId(CharacterUtils.COMMA);
    return splitExternalId==null ? "" : splitExternalId[splitExternalId.length-1];
  }

  private boolean isGeometryOnCorrectSideOfLinkSegment(Geometry geometry, LinkSegment linkSegment, boolean shouldBeOnLeft) {
    return shouldBeOnLeft == data.getGeoTools().isGeometryLeftOf(geometry, linkSegment.getUpstreamVertex().getPosition().getCoordinate(), linkSegment.getDownstreamVertex().getPosition().getCoordinate());
  }

  /**
   * Verify based on driving direction and orientation of the access link segment(s) whether the GTFS stop is a viable match for the
   * found transfer zone in terms of being on the correct side of the road. The assumption here is that this pertains to a road based stop
   * not rail and connectoids being available for the provided transfer zone to extract this information
   *
   * @param gtfsStop to verify
   * @param transferZone to verify against
   * @return true when on correct side of the road, false otherwise
   */
  private boolean isGtfsStopOnCorrectSideOfTransferZoneAccessLinkSegments(GtfsStop gtfsStop, TransferZone transferZone) {
    boolean leftHandDrive = isLeftHandDrive(data.getSettings().getCountryName());
    var connectoids = data.getTransferZoneConnectoids(transferZone);
    if(connectoids== null || connectoids.isEmpty()){
      LOGGER.warning(String.format("Cannot determine of GTFS stop (%s) is on correct side of transfer zone (%s) access links since transfer zone has no connectoids associated with it, this shouldn't happen", gtfsStop.getStopId(), transferZone.getXmlId()));
      return false;
    }

    for (var connectoid : connectoids) {
      var accessSegment = connectoid.getAccessLinkSegment();
      var localProjection = PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());
      if(localProjection==null){
        return false;
      }
      if(!isGeometryOnCorrectSideOfLinkSegment(localProjection, accessSegment, leftHandDrive)){
        /* not compatible for one of its access link segments, */
        return false;
      }
    }
    return true;
  }

  /**
   * Find nearby zones based on a given search radius
   * @param location point location to search around (in WGS84 CRS)
   * @param pointSearchRadiusMeters search radius to apply
   * @return found transfer zones around this location (in network CRS)
   */
  private Collection<TransferZone> findNearbyTransferZones(Point location, double pointSearchRadiusMeters) {
    //todo change implementation so it does not necessarily require WGS84 input locations as it is inconsistent with the utils class
    var searchEnvelope = data.getGeoTools().createBoundingBox(location.getX(),location.getY(),pointSearchRadiusMeters);
    searchEnvelope = PlanitJtsUtils.transformEnvelope(searchEnvelope, data.getCrsTransform());
    return GeoContainerUtils.queryZoneQuadtree(data.getGeoIndexedTransferZones(), searchEnvelope);
  }

  /**
   * Find nearby links based on a given search radius
   * @param location point location to search around (in WGS84 CRS)
   * @param pointSearchRadiusMeters search radius to apply
   * @return found links around this location (in network CRS)
   */
  private Collection<MacroscopicLink> findNearbyLinks(Point location, double pointSearchRadiusMeters) {
    //todo change implementation so it does not necessarily require WGS84 input locations as it is inconsistent with the utils class
    var searchEnvelope = data.getGeoTools().createBoundingBox(location.getX(),location.getY(),pointSearchRadiusMeters);
    searchEnvelope = PlanitJtsUtils.transformEnvelope(searchEnvelope, data.getCrsTransform());
    return GeoContainerUtils.queryEdgeQuadtree(data.getGeoIndexedExistingLinks(), searchEnvelope);
  }

  /**
   * find the transfer zone (underlying stop locations if any) closest to the provided GTFS stop location. In case the transfer zone has no
   * stop locations registered, we use its overall geometry to match the distance to the GTFS stop location.
   *
   * @param gtfsStopLocation tofind closest transfer zone for
   * @param nearbyTransferZones to consider
   * @return found closest transfer zone
   */
  private Pair<TransferZone,Double> findTransferZoneStopLocationClosestTo(Coordinate gtfsStopLocation, Collection<TransferZone> nearbyTransferZones) {
    TransferZone closest = nearbyTransferZones.iterator().next();
    if(nearbyTransferZones.size()==1) {
      return Pair.of(closest, PlanitEntityGeoUtils.getDistanceToZone(gtfsStopLocation, closest, data.getGeoTools()));
    }

    /* multiple options -> use transfer zone geometry or underlying connectoid access nodes (stop locations) */
    double minDistance = Double.POSITIVE_INFINITY;
    final var allowCentroidGeometry = true;
    for(var transferZone : nearbyTransferZones) {
      var directedConnectoids = data.getTransferZoneConnectoids(transferZone);

      /* transfer zone geometry based */
      if (directedConnectoids == null || directedConnectoids.isEmpty()) {
        var planitTransferZoneStopLocation = transferZone.getGeometry(allowCentroidGeometry).getCentroid().getCoordinate();
        double distance = data.getGeoTools().getDistanceInMetres(gtfsStopLocation, planitTransferZoneStopLocation);
        if (minDistance > distance) {
          closest = transferZone;
          minDistance = distance;
        }
      } else {
        /* connectoid access node based */
        for (var dirConnectoid : directedConnectoids) {
          var planitTransferZoneStopLocation = dirConnectoid.getAccessNode().getPosition().getCoordinate();
          double distance = data.getGeoTools().getDistanceInMetres(gtfsStopLocation, planitTransferZoneStopLocation);
          if (minDistance > distance) {
            closest = transferZone;
            minDistance = distance;
          }
        }
      }
    }
    return Pair.of(closest,minDistance);
  }

  /** From the provided options, select the most appropriate based on proximity, mode compatibility, relative location to GTFS stop zone, and importance of the link segment
   *
   * @param gtfsStop under consideration
   * @param accessMode access mode to use
   * @param eligibleLinks for connectoids
   * @return most appropriate link that is found, null if no compatible links could be found
   */
  private MacroscopicLink findMostAppropriateStopLocationLinkForGtfsStop(GtfsStop gtfsStop, Mode accessMode, Collection<MacroscopicLink> eligibleLinks) {
    final boolean stopLocationDirectionSpecific = !(accessMode.getPhysicalFeatures().getTrackType() == TrackModeType.RAIL);
    final Point projectedGtfsStopLocation = (Point) PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());
    final boolean leftHandDrive = isLeftHandDrive(data.getSettings().getCountryName());
    var accessModeAsCollection = Collections.singleton(accessMode);

    /* remove closest roads if incompatible regarding driving direction (relative location of waiting area versus road)
     * if not, we move to salvaging state and those links are removed from the eligible set */
    Pair<MacroscopicLink,Boolean> eligibleClosestPair =
        ZoningConverterUtils.excludeClosestLinksIncrementallyOnWrongSideOf(projectedGtfsStopLocation, eligibleLinks, leftHandDrive,  accessModeAsCollection, data.getGeoTools());

    MacroscopicLink selectedAccessLink = eligibleClosestPair.first();
    boolean salvaging = eligibleClosestPair.second();
    if(selectedAccessLink == null) {
      return null;
    }

    /* reduce options based on proximity to closest viable link, while removing options outside of the closest distance buffer */
    var candidatesToFilterMap = PlanitGraphGeoUtils.findEdgesWithinClosestDistanceDeltaToGeometry(projectedGtfsStopLocation, eligibleLinks, GtfsZoningReaderSettings.DEFAULT_CLOSEST_LINK_SEARCH_BUFFER_DISTANCE_M, data.getGeoTools());
    var candidatesToFilter = ((Map<MacroscopicLink, Double>)candidatesToFilterMap).keySet().stream().collect(Collectors.toSet());
    if(candidatesToFilter == null || candidatesToFilter.isEmpty()){
      throw new PlanItRunTimeException("No closest link could be found from selection of eligible close by links when finding potential access links for GTFS STOP (%s), this should not happen", gtfsStop.getStopId());
    }else if(candidatesToFilter.size()>1){
      /* more than a single candidate, proceed elimination or replace current closest with more appropriate option if found */
      selectedAccessLink = null; // consider all remaining options again

      /* 1) reduce options by removing all compatible links within proximity of the closest link that are on the wrong side of the road infrastructure */
      candidatesToFilter = (Set<MacroscopicLink>) ZoningConverterUtils.excludeLinksOnWrongSideOf(projectedGtfsStopLocation,  candidatesToFilter, leftHandDrive, accessModeAsCollection, data.getGeoTools());

      /* 2) make sure a valid stop_location on each remaining link can be created (for example if stop_location would be on an extreme node, it is possible no access link segment upstream of that node remains
       *    which would render an otherwise valid position invalid */
      //todo --> continue within this method first to fix up Planit core with the utils that we are implementing to support both OSM and GTFS, then continue here below
      candidatesToFilter.removeIf(
          l -> null == ZoningConverterUtils.findConnectoidLocationForWaitingAreaOnLink(projectedGtfsStopLocation, l, accessMode, getSettings().getStopToWaitingAreaSearchRadiusMeters()));

      //todo
//      if(candidatesToFilter == null || candidatesToFilter.isEmpty() ) {
//        logWarningIfNotNearBoundingBox(String.format("DISCARD: No suitable stop_location on potential osm way candidates found for transfer zone %s and mode %s", transferZone.getExternalId(), accessMode.getName()), transferZone.getGeometry());
//        return null;
//      }

      /* 3) filter based on link hierarchy using osm way types, the premise being that bus services tend to be located on main roads, rather than smaller roads
       * there is no hierarchy for rail, so we only do this for road modes. This could allow slightly misplaced waiting areas with multiple options near small and big roads
       * to be salvaged in favour of the larger road */
      //todo
//      if(OsmRoadModeTags.isRoadModeTag(osmAccessMode)){
//        OsmWayUtils.removeEdgesWithOsmHighwayTypesLessImportantThan(OsmWayUtils.findMostProminentOsmHighWayType(candidatesToFilter), candidatesToFilter);
//      }

      //todo
//      if(candidatesToFilter.size()==1) {
//        selectedAccessLink = candidatesToFilter.iterator().next();
//      }else {
//        /* 4) still multiple options, now select closest from the remaining candidates */
//        selectedAccessLink = (MacroscopicLink)PlanitGraphGeoUtils.findEdgeClosest(transferZone.getGeometry(), candidatesToFilter, getGeoUtils());
//      }

    }

    //todo
//    if(salvaging == true) {
//      LOGGER.info(String.format("SALVAGED: Used non-closest osm way to %s %s to ensure waiting area %s is on correct side of road for mode %s",
//          selectedAccessLink.getExternalId(), selectedAccessLink.getName() != null ?  selectedAccessLink.getName() : "" , transferZone.getExternalId(), osmAccessMode));
//    }

    return selectedAccessLink;
  }


  /** Based on coordinate draw a virtual line to closest intersection point on link segment and identify the azimuth (0-360 degrees) that goes with this virtual line
   *  from the intersection point on the link segment towards the coordinate provided
   *
   * @param linkSegment to use
   * @param coordinate to use
   * @return azimuth in degrees found
   */
  private double getAzimuthFromLinkSegmentToCoordinate(LinkSegment linkSegment, Coordinate coordinate) {
    var linkSegmentGeometry = linkSegment.getParentLink().getGeometry();
    var closestLinkIntersect = data.getGeoTools().getClosestProjectedLinearLocationOnLineString(coordinate, linkSegmentGeometry);
    var closestLinkIntersectCoordinate = closestLinkIntersect.getCoordinate(linkSegmentGeometry);

    var dirPos1 = data.getGeoTools().toDirectPosition(closestLinkIntersectCoordinate);
    var dirPos2 =  data.getGeoTools().toDirectPosition(coordinate);
    return data.getGeoTools().getAzimuthInDegrees(dirPos1,dirPos2, true);
  }

  /**
   * Match first transfer zone in collecton which has a platform name that equals the GTFS stops platform code
   *
   * @param gtfsStop to match against
   * @param transferZones to check against
   * @return found transfer zone, null if none found
   */
  private TransferZone matchByPlatform(GtfsStop gtfsStop, Collection<TransferZone> transferZones) {
    /* platform codes should only be present when platform is part of a station */
    if(!gtfsStop.hasPlatformCode()){
      return null;
    }

    /* find transfer zone with first matching platform name */
    for(var transferZone : transferZones){
      var platformNames = transferZone.getTransferZonePlatformNames();
      if(platformNames != null && platformNames.stream().filter( name -> name.equalsIgnoreCase(gtfsStop.getPlatformCode())).findFirst().isPresent()){
        return transferZone;
      }
    }

    return null;
  }

  /**
   * Process a GTFS stop that could not be matched to an existing transfer zone. It will trigger the
   * creation of a new transfer zone on the PLANit zoning
   *
   * @param gtfsStop to create new TransferZone for
   */
  private void createNewTransferZone(GtfsStop gtfsStop) {
  }

  /**
   * Process a GTFS stop that could be matched to nearby existing transfer zone(s). If a match is found it will provide the match, of not null is returned
   *
   * @param gtfsStop      to create new TransferZone for
   * @return found match (not attached yet), null if no match is found
   */
  private TransferZone findMatchingExistingTransferZone(final GtfsStop gtfsStop, final Collection<TransferZone> nearbyTransferZones) {
    PlanItRunTimeException.throwIfNull(gtfsStop,"GTFS stop null, this is not allowed");
    PlanItRunTimeException.throwIfNullOrEmpty(nearbyTransferZones,"No nearby transfer zones provided, this is not allowed");

    final Mode gtfsStopMode = data.getSupportedPtMode(gtfsStop);
    final boolean stopLocationDirectionSpecific = !(gtfsStopMode.getPhysicalFeatures().getTrackType() == TrackModeType.RAIL);
    final Point projectedGtfsStopLocation = (Point) PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());

    // prune transfer zones
    {
      /* remove nearby zones that are not mode compatible */
      nearbyTransferZones.removeIf(tz -> data.getSupportedPtModesIn(tz, Set.of(gtfsStopMode)).isEmpty());
      if(nearbyTransferZones.isEmpty()){
        return null;
      }

      /* remove transfer zones not on the correct side of the road, where rail is acceptable in both directions always */
      nearbyTransferZones.removeIf(tz -> stopLocationDirectionSpecific && !isGtfsStopOnCorrectSideOfTransferZoneAccessLinkSegments(gtfsStop, tz));
      if(nearbyTransferZones.isEmpty()) {
        return null;
      }
    }

    /* try to match on platform name first as it is most trustworthy... */
    var matchedTransferZone = matchByPlatform(gtfsStop, nearbyTransferZones);
    if(matchedTransferZone != null){
      data.getProfiler().incrementMatchedTransferZonesOnPlatformName();
      return matchedTransferZone;
    }

    var nearbyLinks = findNearbyLinks(gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToLinkSearchRadiusMeters());
    //todo first continue with completing this method and refactoring/reusing things from the OSM parser that also does this...
    MacroscopicLink accessLink = findMostAppropriateStopLocationLinkForGtfsStop(gtfsStop, gtfsStopMode, nearbyLinks);
    //todo: get the access link segemtn that ges with this
    MacroscopicLinkSegment accessLinkSegment = null;

    do {
      /* no definite match yet, ... find closest existing transfer zone */
      matchedTransferZone = findTransferZoneStopLocationClosestTo(projectedGtfsStopLocation.getCoordinate(), nearbyTransferZones).first();

      /* verify that closest transfer zone is virtually attached to the road network roughly using the same virtual direction as if we were to connect
       * the gtfs stop to the nearest eligible link segment (mode and direction compatible). check by using azimuth and virtual line segment from these points to road network
       */
      var directedConnectoids = data.getTransferZoneConnectoids(matchedTransferZone);
      NEXT:
      for (var cn : directedConnectoids) {
        //todo: not sure if we should check all connectoids, maybe first check if there is any connectoid that has a decent angle it should be fine?
        // todo: also check if any of the connectoids have an access link segment matching the found access link segment for the GTFS stop, if so, it is a match too
        var zoneGeoCentroid = (Point) matchedTransferZone.getGeometry(true).getCentroid();
        double tzAzimuth = getAzimuthFromLinkSegmentToCoordinate(cn.getAccessLinkSegment(), zoneGeoCentroid.getCoordinate());
        var gtfsAzimuth= getAzimuthFromLinkSegmentToCoordinate(accessLinkSegment, projectedGtfsStopLocation.getCoordinate());
        double diffAngle = PlanitJtsUtils.minDiffAngleInDegrees(gtfsAzimuth,tzAzimuth);
        if ( diffAngle > 100) {
          nearbyTransferZones.remove(matchedTransferZone);
          matchedTransferZone = null;
          break NEXT;
        }
      }
    }while (matchedTransferZone == null && !nearbyTransferZones.isEmpty());
    if (matchedTransferZone == null) {
      return null;
    }

    /* when non rail mode, we would expect only a single mapping, log info when we find multiple to let user verify*/
    if (data.hasMappedGtfsStop(matchedTransferZone) && !(gtfsStopMode.getPhysicalFeatures().getTrackType() == TrackModeType.RAIL)) {
      var earlierMappedStop = data.getMappedGtfsStop(getLastTransferZoneExternalId(matchedTransferZone));
      LOGGER.warning(String.format("PLANit transfer zone (%s) already mapped to GTFS stop (%s, %s, %s), adding another mapping for GTFS STOP (%s, %s, %s), consider verifying correctness",
          matchedTransferZone.getXmlId(), earlierMappedStop.getStopId(), earlierMappedStop.getStopName(), earlierMappedStop.getLocationAsCoord().toString(), gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord().toString()));
    }
    return matchedTransferZone;
  }

  /**
   * Attach the GTFS stop to the given transfer zone
   *
   * @param gtfsStop to attach
   * @param transferZone to attach to
   */
  private void attachToTransferZone(GtfsStop gtfsStop, TransferZone transferZone) {
    /* augment external id with GTFS stop id + index by this id in separate map */
    if(!transferZone.getExternalId().contains(gtfsStop.getStopId())) {
      transferZone.appendExternalId(gtfsStop.getStopId());
    }

    /* supplement platform information if any is present on GTFS but not on transfer zone so far */
    if(gtfsStop.hasPlatformCode()){
      transferZone.addTransferZonePlatformName(gtfsStop.getPlatformCode());
    }

    /* update tracking data */
    data.registerMappedGtfsStop(gtfsStop, transferZone);
    LOGGER.info(String.format("Mapped GTFS stop %s at location %s to existing Transfer zone %s",gtfsStop.getStopId(), gtfsStop.getLocationAsCoord().toString(), transferZone.getXmlId()));

    //todo -> now either use the existing mapping to the physical network to update the reference on the service node of the stop
    //        or alternatively if there is no mapping yet create the mapping (isolate the OSM code that does this, make it generic and reuse it)

    //todo: if we reuse this for newly created transfer zones we should make the below dependent on a flag */
    /* profiler */
    data.getProfiler().incrementAugmentedTransferZones();
  }

  /**
   * Process the GTFS stop which is marked as stop platform and fuse it with the PLANit memory model and previously parsed GTFS entities
   *
   * @param gtfsStop to processs
   */
  private void handleStopPlatform(GtfsStop gtfsStop) {
    data.getProfiler().incrementCount(GtfsObjectType.STOP);

    final Mode gtfsStopMode = data.getSupportedPtMode(gtfsStop);
    LoggingUtils.LogSevereIfNull(gtfsStopMode, LOGGER, "GTFSStop %s has no known PLANit mode that uses the stop", gtfsStop.getStopId());
    if(!data.getSettings().getAcivatedPlanitModes().contains(gtfsStopMode)){
      return;
    }

    Collection<TransferZone> nearbyTransferZones = findNearbyTransferZones(gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters());
    if(nearbyTransferZones.isEmpty()){
      createNewTransferZone(gtfsStop);
      return;
    }

    var foundMatch = findMatchingExistingTransferZone(gtfsStop, nearbyTransferZones);
    if(foundMatch != null){
      attachToTransferZone(gtfsStop, foundMatch);
    }else{
      createNewTransferZone(gtfsStop);
    }
  }

  /**
   * Constructor
   *
   * @param data all handler related data is provided, tracked via this instance
   */
  public GtfsPlanitFileHandlerStops(GtfsZoningHandlerData data) {
    super();
    this.data = data;

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

