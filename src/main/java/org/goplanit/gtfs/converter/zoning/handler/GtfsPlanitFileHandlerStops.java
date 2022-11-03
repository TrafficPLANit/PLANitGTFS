package org.goplanit.gtfs.converter.zoning.handler;

import org.geotools.geometry.jts.JTS;
import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.*;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.misc.CharacterUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.TransferZone;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  /**
   * From the provided options, select the most appropriate based on proximity, mode compatibility, relative location to GTFS stop zone, and importance of the link segment.
   * In case of track based modes either direction link segment is acceptable, otherwise the correctly facing link segment is chosen
   *
   * @param gtfsStop      under consideration
   * @param accessMode    access mode to use
   * @param eligibleLinks for connectoids
   * @return most appropriate link that is found and its eligible access link segment(s), null if no compatible links could be found
   */
  private Pair<MacroscopicLink, List<EdgeSegment>> findMostAppropriateStopLocationLinkForGtfsStop(GtfsStop gtfsStop, Mode accessMode, Collection<MacroscopicLink> eligibleLinks) {
    final Point projectedGtfsStopLocation = (Point) PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());

    Function<MacroscopicLink, String> linkToSourceId = l -> l.getExternalId(); // make configurable as used for overwrites
    final Function<Node,String> getOverwrittenWaitingAreaSourceIdForNode = null;   // not relevant as gtfs data has no stop_locations (node) on links to map to GTFS stops (waiting area)
    final Function<Point,String> getOverwrittenWaitingAreaSourceIdForPoint = null; // not relevant as gtfs data has no stop_locations (point) on links to map to GTFS stops (waiting area)
    //todo: leave null for now. If this is needed in future, we can add settings to populate this mapping and then use this here to feed into the function
    final Function<String,String> getOverwrittenAccessLinkSourceIdForWaitingAreaSourceId = null;

    /* 1) reduce candidates to access links related to access link segments that are deemed valid in terms of mode and location (closest already complies as per above) */
    List<EdgeSegment> accessLinkSegments = new ArrayList<>(2);
    for(var currAccessLink : eligibleLinks) {
      var currAccessLinkSegments = ZoningConverterUtils.findAccessLinkSegmentsForWaitingArea(
          gtfsStop.getStopId(),
          projectedGtfsStopLocation,
          currAccessLink,
          linkToSourceId.apply(currAccessLink),
          accessMode,
          data.getSettings().getCountryName(),
          true,
          getOverwrittenAccessLinkSourceIdForWaitingAreaSourceId,
          getOverwrittenWaitingAreaSourceIdForNode,
          data.getGeoTools());
      if (currAccessLinkSegments != null) {
        accessLinkSegments.addAll(currAccessLinkSegments);
      }
    }
    // filter candidate links based on link segments found acceptable
    var candidatesToFilter = accessLinkSegments.stream().flatMap( ls -> Stream.of((MacroscopicLink)ls.getParent())).collect(Collectors.toSet());

    /* 2) make sure a valid stop_location on each remaining link can be created (for example if stop_location would be on an extreme node, it is possible no access link segment upstream of that node remains
     *    which would render an otherwise valid position invalid */
    candidatesToFilter.removeIf(
        l -> null == ZoningConverterUtils.findConnectoidLocationForWaitingAreaOnLink(
            gtfsStop.getStopId(),
            projectedGtfsStopLocation,
            l,
            linkToSourceId.apply(l),
            accessMode,
            data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters(),
            getOverwrittenWaitingAreaSourceIdForNode,
            getOverwrittenWaitingAreaSourceIdForPoint,
            getOverwrittenAccessLinkSourceIdForWaitingAreaSourceId,
            data.getSettings().getCountryName(),
            data.getGeoTools()));

    if(candidatesToFilter == null || candidatesToFilter.isEmpty() ) {
      return null;
    }else if(candidatesToFilter.size()==1) {
      var selectedAccessLink = candidatesToFilter.iterator().next();
      accessLinkSegments.removeIf( ls -> !ls.getParent().equals(selectedAccessLink)); // sync
      return Pair.of(selectedAccessLink, accessLinkSegments);
    }

     /* 3) all proper candidates so  reduce options further based on proximity to closest viable link, while removing options outside of the closest distance buffer */
    var filteredCandidates = (Set<MacroscopicLink>)
        PlanitGraphGeoUtils.findEdgesWithinClosestDistanceDeltaToGeometry(projectedGtfsStopLocation, candidatesToFilter, GtfsZoningReaderSettings.DEFAULT_CLOSEST_LINK_SEARCH_BUFFER_DISTANCE_M, data.getGeoTools()).keySet();
    candidatesToFilter = null;
    accessLinkSegments.removeIf( ls -> !filteredCandidates.contains(ls.getParent())); // sync

    if(filteredCandidates.size()==1){
      var selectedAccessLink = filteredCandidates.iterator().next();
      accessLinkSegments.removeIf( ls -> !ls.getParent().equals(selectedAccessLink)); // sync
      return Pair.of(selectedAccessLink, accessLinkSegments);
    }

    /* 4) Remaining options are all valid and close ... choose based on importance, the premise being that road based PT services tend to be located on main roads, rather than smaller roads
     * so we choose the first link segment with the highest capacity found and then return its parent link as the candidate */
    MacroscopicLink selectedAccessLink = null;
    if(!(accessMode.getPhysicalFeatures().getTrackType() == TrackModeType.RAIL) && filteredCandidates.size()>1){
      // take the parent link of the edge segment with the maximum capacity
      selectedAccessLink = (MacroscopicLink)
          accessLinkSegments.stream().max( Comparator.comparingDouble(ls -> ((MacroscopicLinkSegment)ls).getCapacityOrDefaultPcuHLane())).get().getParent();
    }else{
      selectedAccessLink = (MacroscopicLink)PlanitGraphGeoUtils.findEdgeClosest(projectedGtfsStopLocation, filteredCandidates, data.getGeoTools());
    }
    final MacroscopicLink finalSelectedAccessLink = selectedAccessLink;
    accessLinkSegments.removeIf(ls -> !ls.getParent().equals(finalSelectedAccessLink)); // sync

     return Pair.of(selectedAccessLink, accessLinkSegments);
  }


  /** Based on coordinate draw a virtual line to closest intersection point on link segment and identify the azimuth (0-360 degrees) that goes with this virtual line
   *  from the intersection point on the link segment towards the coordinate provided
   *
   * @param linkSegment to use
   * @param coordinate to use
   * @return azimuth in degrees found
   */
  private double getAzimuthFromLinkSegmentToCoordinate(EdgeSegment linkSegment, Coordinate coordinate) {
    PlanItRunTimeException.throwIfNull(linkSegment, "LinkSegment is null");
    PlanItRunTimeException.throwIfNull(coordinate, "Coordinate is null");

    var linkSegmentGeometry = linkSegment.getParent().getGeometry();
    var closestLinkIntersect = data.getGeoTools().getClosestProjectedLinearLocationOnLineString(coordinate, linkSegmentGeometry);
    var closestLinkIntersectCoordinate = closestLinkIntersect.getCoordinate(linkSegmentGeometry);

    var dirPos1 = data.getGeoTools().toDirectPosition(closestLinkIntersectCoordinate);
    var dirPos2 =  data.getGeoTools().toDirectPosition(coordinate);
    return data.getGeoTools().getAzimuthInDegrees(dirPos1,dirPos2, true);
  }

  /**
   * Match first transfer zone in collection which has a platform name that equals the GTFS stops platform code
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
   * Match first transfer zone in collection which has an access link segment for one of its directed connectoids that equals the GTFS stop's preferred
   * access link segment(s)
   *
   * @param gtfsStop to match against
   * @param preferredAccessLinkSegments of the GTFS stop
   * @param transferZones to check against
   * @return found transfer zone, null if none found
   */
  private TransferZone matchByAccessLinkSegments(GtfsStop gtfsStop, List<EdgeSegment> preferredAccessLinkSegments, Collection<TransferZone> transferZones) {
    TransferZone match = null;
    for (var transferZone : transferZones) {
      var directedConnectoids = data.getTransferZoneConnectoids(transferZone);
      for (var cn : directedConnectoids) {
        /* match on link segment */
        if(preferredAccessLinkSegments.contains(cn.getAccessLinkSegment())) {
          var projectedGtfsStopLocation = (Point)PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());

          /* ensure that actual access node is within acceptable distance as well, e.g., if too far away the access link segment is too long and
           * transfer zone considered not close enough, given it is a match on the link segment, we allow for a little more distance, namely the allowed distance to a transfer zone + the allowed distance from transfer zone(stop) to the road*/
          final var maxStopToAccessNodeDistanceForMatchedTransferZonesWithEqualAccessLinkSegment = data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters() + data.getSettings().getGtfsStopToLinkSearchRadiusMeters();
          if(data.getGeoTools().isDistanceWithinMetres(projectedGtfsStopLocation, cn.getAccessNode().getPosition(), maxStopToAccessNodeDistanceForMatchedTransferZonesWithEqualAccessLinkSegment)) {
            if (match != null) {
              LOGGER.warning(
                  String.format("Multiple matching transfer zones found (PLANit XML ids: %s, %s) which have the same access link segment preferred by GTFS stop (%s), choosing first", match.getXmlId(), transferZone.getXmlId(), gtfsStop.getStopId()));
            } else {
              match = transferZone;
            }
          }else{
            LOGGER.info(
                String.format("GTFS stop %s %s (location %s) initially matched to transfer zone (XML id: %s, ext id: %s) sharing same preferred access link segment, but access node too far away, match ignored", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), transferZone.getXmlId(), transferZone.getExternalId()));
          }
        }
      }
    }
    return match;
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

    /* identify preferred access link (segments) for GTFS stop as if there were no existing transfer zones to map to, to use for matching */
    var nearbyLinks = findNearbyLinks(gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToLinkSearchRadiusMeters());
    Pair<MacroscopicLink, List<EdgeSegment>> accessResult = findMostAppropriateStopLocationLinkForGtfsStop(gtfsStop, gtfsStopMode, nearbyLinks);
    if(accessResult == null){
      LOGGER.severe(String.format("No appropriate stop location on the network could be found for GTFS stop %s",gtfsStop.getStopId()));
    }

    /* try to match based on preferred access link segments versus used access link segments by existing nearby transfer zones */
    matchedTransferZone = matchByAccessLinkSegments(gtfsStop, accessResult.second(), nearbyTransferZones);
    if(matchedTransferZone != null){
      data.getProfiler().incrementMatchedTransferZonesOnAccessLinkSegment();
      return matchedTransferZone;
    }

    matchedTransferZone = null;
    while (matchedTransferZone == null && !nearbyTransferZones.isEmpty()){
      /* no match yet, ... find closest existing transfer zone and verify if angle of its access link segment is appropriate for our gtfs stop*/
      matchedTransferZone = findTransferZoneStopLocationClosestTo(projectedGtfsStopLocation.getCoordinate(), nearbyTransferZones).first();
      if(accessResult.second().size()>1){
        break; // track based mode, so access is guaranteed on any side of stop location, just select this closest stop as the match
      }
      var accessLinkSegment = accessResult.second().iterator().next();

      /* verify that closest transfer zone is virtually attached to the road network roughly using the same virtual direction as if we were to connect
       * the gtfs stop to the nearest eligible link segment (mode and direction compatible). check by using azimuth and virtual line segment from these points to road network */
      var directedConnectoids = data.getTransferZoneConnectoids(matchedTransferZone);
      boolean connectoidMatch = false;
      NEXT:
      for (var cn : directedConnectoids) {

        // check if connectoid within acceptable distance
        if(!data.getGeoTools().isDistanceWithinMetres(projectedGtfsStopLocation, cn.getAccessNode().getPosition(),data.getSettings().getGtfsStopToLinkSearchRadiusMeters())) {
          continue;
        }

        // check angle of
        var zoneGeoCentroid = (Point) matchedTransferZone.getGeometry(true).getCentroid();
        double tzAzimuth = getAzimuthFromLinkSegmentToCoordinate(cn.getAccessLinkSegment(), zoneGeoCentroid.getCoordinate());
        var gtfsAzimuth= getAzimuthFromLinkSegmentToCoordinate(accessLinkSegment, projectedGtfsStopLocation.getCoordinate());
        double diffAngle = PlanitJtsUtils.minDiffAngleInDegrees(gtfsAzimuth,tzAzimuth);
        if ( diffAngle < 100) {
          connectoidMatch = true;
          break NEXT;
        }
      }
      if(!connectoidMatch) {
        nearbyTransferZones.remove(matchedTransferZone);
        matchedTransferZone = null;
      }
    }
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
    // when mapping exists to physical network for transfer zone and gtfs stop has different access link segment it prefers. Find out if it should be added
    // or not. For example: when the access link segment has the same downstream node but is a different entry link segment it should be added, but if
    // it has a different downstream node then the existing transfer zone's access nodes it might well be that it is a broken link which feeds into an already
    // existing access link segment for the transfer zone:    NEW GTFS       existing access link seg of transfer zone
    //                                                    *------->-------*------>------*
    // in the latter case we should not create a new access link segment but reuse the existing one it ends up in!

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
    LoggingUtils.LogFineIfNull(gtfsStopMode, LOGGER, "GTFS Stop %s %s (location: %s) unknown mapped PLANit mode; likely stop is not used by GTFS, or stop's mode is not activated", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord());

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

  private void handleOverwrittenTransferZoneMapping(GtfsStop gtfsStop) {
    //todo: implement
    throw new PlanItRunTimeException("overwritten mapping of GTFFS stop to existing Transfer zone not yet implemented");
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

    if(data.getSettings().isOverwrittenGtfsStopTransferZoneMapping(gtfsStop.getStopId())){
      handleOverwrittenTransferZoneMapping(gtfsStop);
    }

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

