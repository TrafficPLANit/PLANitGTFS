package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;
import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.gtfs.util.GtfsDirectedConnectoidHelper;
import org.goplanit.gtfs.util.GtfsLinkHelper;
import org.goplanit.gtfs.util.GtfsLinkSegmentHelper;
import org.goplanit.gtfs.util.GtfsTransferZoneHelper;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.*;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneType;
import org.locationtech.jts.geom.Point;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  /**
   * From the provided options, select the most appropriate based on proximity, mode compatibility, relative location to GTFS stop zone, and importance of the link segment.
   * In case of track based modes either direction link segment is acceptable, otherwise the correctly facing link segment is chosen
   *
   * @param gtfsStop      under consideration
   * @param eligibleAccessModes access modes considered eligible to use
   * @param eligibleLinks for connectoids
   * @return most appropriate link that is found and its eligible access link segment(s), null if no compatible links could be found
   */
  private Pair<MacroscopicLink, Set<LinkSegment>> findMostAppropriateStopLocationLinkForGtfsStop(
      GtfsStop gtfsStop, Collection<Mode> eligibleAccessModes, Collection<MacroscopicLink> eligibleLinks) {
    final Point projectedGtfsStopLocation = (Point) PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());

    Function<MacroscopicLink, String> linkToSourceId = l -> l.getExternalId(); // make configurable as used for overwrites

    /* 1) reduce candidates to access links related to access link segments that are deemed valid in terms of mode and location (closest already complies as per above) */
    Set<LinkSegment> accessLinkSegments = new HashSet<>(2);
    for(var currAccessLink : eligibleLinks) {
      for(var accessMode : eligibleAccessModes) {
        var currAccessLinkSegments = ZoningConverterUtils.findAccessLinkSegmentsForWaitingArea(
            gtfsStop.getStopId(),
            projectedGtfsStopLocation,
            currAccessLink,
            linkToSourceId.apply(currAccessLink),
            accessMode,
            data.getSettings().getCountryName(),
            true, null, null, data.getGeoTools());
        if (currAccessLinkSegments != null) {
          accessLinkSegments.addAll(currAccessLinkSegments);
        }
      }
    }
    // extract parent links from options
    var candidatesToFilter =
        accessLinkSegments.stream().flatMap( ls -> Stream.of((MacroscopicLink)ls.getParent())).collect(Collectors.toSet());

    /* 2) make sure a valid stop_location on each remaining link can be created (for example if stop_location would be on an extreme node, it is possible no access link segment upstream of that node remains
     *    which would render an otherwise valid position invalid */
    var candidatesWithValidConnectoidLocation = new HashSet<MacroscopicLink>();
    for(var candidate : candidatesToFilter){
      for(var accessMode : eligibleAccessModes) {
        if(null != ZoningConverterUtils.findConnectoidLocationForWaitingAreaOnLink(
            gtfsStop.getStopId(),
            projectedGtfsStopLocation,
            candidate,
            linkToSourceId.apply(candidate),
            accessMode,
            data.getSettings().getGtfsStopToLinkSearchRadiusMeters(),
            null,
            null,
            null,
            data.getSettings().getCountryName(),
            data.getGeoTools())){
          candidatesWithValidConnectoidLocation.add(candidate);
          break;
        }
      }
    }

    if(candidatesWithValidConnectoidLocation.isEmpty() ) {
      return null;
    }else if(candidatesWithValidConnectoidLocation.size()==1) {
      var selectedAccessLink = candidatesWithValidConnectoidLocation.iterator().next();
      accessLinkSegments.removeIf( ls -> !ls.getParent().equals(selectedAccessLink)); // sync
      return Pair.of(selectedAccessLink, accessLinkSegments);
    }

     /* 3) all proper candidates so  reduce options further based on proximity to closest viable link, while removing options outside of the closest distance buffer */
    var filteredCandidates =
        PlanitGraphGeoUtils.findEdgesWithinClosestDistanceDeltaToGeometry(
            projectedGtfsStopLocation, candidatesWithValidConnectoidLocation, GtfsZoningReaderSettings.DEFAULT_CLOSEST_LINK_SEARCH_BUFFER_DISTANCE_M, data.getGeoTools()).keySet();
    accessLinkSegments.removeIf( ls -> !filteredCandidates.contains(ls.getParent())); // sync

    if(filteredCandidates.size()==1){
      var selectedAccessLink = filteredCandidates.iterator().next();
      accessLinkSegments.removeIf( ls -> !ls.getParent().equals(selectedAccessLink)); // sync
      return Pair.of(selectedAccessLink, accessLinkSegments);
    }

    /* 4) Remaining options are all valid and close ... choose based on importance, the premise being that road based PT services tend to be located on main roads, rather than smaller roads
     * so we choose the first link segment with the highest capacity found and then return its parent link as the candidate */
    MacroscopicLink selectedAccessLink = null;
    if(!(eligibleAccessModes.stream().findAny().get().getPhysicalFeatures().getTrackType() == TrackModeType.RAIL) && filteredCandidates.size()>1){
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
   * @param maxStopToAccessNodeDistanceMeters the maximum allowed distances between GTFS stop and the access node of the matched access link segment
   * @return found transfer zone and its matched directed connectoid (and its access link segment) that matches the GTFS stop, null if none found
   */
  private Pair<TransferZone,DirectedConnectoid> matchByAccessLinkSegments(
      final GtfsStop gtfsStop, final Collection<LinkSegment> preferredAccessLinkSegments, final Collection<TransferZone> transferZones, final double maxStopToAccessNodeDistanceMeters) {
    TransferZone match = null;
    DirectedConnectoid matchedConnectoid = null;
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
                  String.format("Multiple matching transfer zones found (PLANit XML ids: %s, %s) which have the same access link segment preferred by GTFS stop %s %s (location %s), choosing first", match.getXmlId(), transferZone.getXmlId(), gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord()));
            } else {
              match = transferZone;
              matchedConnectoid = cn;
            }
          }else{
            LOGGER.info(
                String.format("GTFS stop %s %s (location %s) initially matched to transfer zone (XML id: %s, ext id: %s) sharing same preferred access link segment, but access node too far away, match ignored", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), transferZone.getXmlId(), transferZone.getExternalId()));
          }
        }
      }
    }
    return Pair.of(match,matchedConnectoid);
  }

  /**
   * Match available transfer zones to the gtfs stop with a given number of preferred access segments. These segments for road based should only contain a single directional segment whereas for
   * track based, they can contain multiple (as tracks have no direction, both directions are acceptable). We then cycle through the closest eligible transfer zones and for each determine the angle
   * between a virtual line connecting the zone-to-road vs the gtfs-stop-to-road is below the given threshold. If it is a match is found which is then verified to be reachable. The latter meaning that
   * the access link segment of the gtfs stop should be adjacent to a transfer zone's access link segment with acceptable angle. If so it is a proper match. If only the angle matches we log a warning
   * for the user to check if it is indeed valid.
   *
   * @param gtfsStop                          to match
   * @param gtfsStopMode                      mode of the stop
   * @param gtfsAccessSegments                GTFS stop elgible access link segments
   * @param nearbyTransferZones               to match with
   * @param maxAngleDegrees                   to allow
   * @param maxStopToAccessNodeDistanceMeters the maximum allowed distances between GTFS stop and the access node of the matched access link segment
   * @return matches transfer zone, null if no valid match is found
   */
  private TransferZone matchByClosestWithAcceptableAccessAngle(
      GtfsStop gtfsStop, Mode gtfsStopMode, Collection<LinkSegment> gtfsAccessSegments, Collection<TransferZone> nearbyTransferZones, final double maxAngleDegrees, final double maxStopToAccessNodeDistanceMeters) {
    TransferZone matchedTransferZone = null;
    final Point projectedGtfsStopLocation = (Point) PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());

    final boolean allowUTurn = false;
    boolean adjacentMatch = false;
    var accessLinkSegment = gtfsAccessSegments.iterator().next();
    while (!adjacentMatch && !nearbyTransferZones.isEmpty()){
      /* no match yet, ... find closest existing transfer zone and verify if angle of its access link segment is appropriate for our gtfs stop*/
      var currTransferZone = GtfsTransferZoneHelper.findTransferZoneStopLocationClosestTo(projectedGtfsStopLocation.getCoordinate(), nearbyTransferZones, data).first();
      if(gtfsAccessSegments.size()>1){
        break; // track based mode, so access is guaranteed on any side of stop location, just select this closest stop as the match
      }

      /* verify that closest transfer zone is virtually attached to the road network roughly using the same virtual direction as if we were to connect
       * the gtfs stop to the nearest eligible link segment (mode and direction compatible). check by using azimuth and virtual line segment from these points to road network */
      var directedConnectoids = data.getTransferZoneConnectoids(currTransferZone);
      boolean angleMatchFound = false;
      NEXT:
      for (var cn : directedConnectoids) {

        // check if connectoid within acceptable distance
        if(!data.getGeoTools().isDistanceWithinMetres(projectedGtfsStopLocation, cn.getAccessNode().getPosition(),maxStopToAccessNodeDistanceMeters)) {
          continue;
        }

        // check if mode compatible
        if(!((MacroscopicLinkSegment)cn.getAccessLinkSegment()).isModeAllowed(gtfsStopMode)){
          continue;
        }

        // check angle difference between shortest zone-to-road virtual line
        var zoneGeoCentroid = (Point) currTransferZone.getGeometry(true).getCentroid();
        double tzAzimuth = GtfsLinkSegmentHelper.getAzimuthFromLinkSegmentToCoordinate(cn.getAccessLinkSegment(), zoneGeoCentroid.getCoordinate(), data);
        var gtfsAzimuth= GtfsLinkSegmentHelper.getAzimuthFromLinkSegmentToCoordinate(accessLinkSegment, projectedGtfsStopLocation.getCoordinate(), data);
        double diffAngle = PlanitJtsUtils.minDiffAngleInDegrees(gtfsAzimuth,tzAzimuth);
        if ( diffAngle < maxAngleDegrees) {
          // tentative match to register
          angleMatchFound = true;
          // check adjacency. Can happen that we have no adjacency meaning either that this is a train platform and we have to try the other
          // connectoids first that likely will be adjacent, or it appears to be match but it might be an anomaly with close together stops in which case
          // we will log a warning after checking all other options first
          adjacentMatch = accessLinkSegment.isAdjacent(cn.getAccessLinkSegment(), allowUTurn);
        }

        if(adjacentMatch){
          break NEXT;
        }
      }

      // update match
      if(angleMatchFound && (matchedTransferZone== null || adjacentMatch)) {
        matchedTransferZone = currTransferZone;
      }else {
        nearbyTransferZones.remove(currTransferZone);
      }
    }

    // notify user of a match found but preferred GTFS access link segment - while having the right angle - does not appear to be directly adjacent (possibly not reachable), let user decide what to do but keep mapping
    if(matchedTransferZone!=null && !adjacentMatch){
      LOGGER.warning(String.format(
          "GTFS stop %s %s (location %s) mapped to transfer zone (XmlId: %s ExtId:%s), but GTFS preferred access link segment (XmlId: %s, ExtId: %s) not adjacent to existing access link segments, verify correctness",gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), matchedTransferZone.getXmlId(), matchedTransferZone.getExternalId(), accessLinkSegment.getXmlId(), accessLinkSegment.getExternalId()));
    }

    return matchedTransferZone;
  }

  /**
   * Process a GTFS stop that could be matched to nearby existing transfer zone(s). If a match is found it will provide the match, of not null is returned
   *
   * @param gtfsStop      to create new TransferZone for
   * @param primaryMode PLANit mode associated with GTFS stop
   * @param nearbyTransferZones to consider, note this container will be pruned if zones are not eligible
   * @return found match (not attached yet), null if no match is found, the mode can be the primary mode initially provided, or a compatible alternative mode
   *          that is deemed a valid alternative. If the latter is the case, the found transfer zone is not compatible with the primary mode
   */
  private TransferZone findMatchingExistingTransferZone(final GtfsStop gtfsStop, final Mode primaryMode, final Collection<TransferZone> nearbyTransferZones) {
    PlanItRunTimeException.throwIfNull(gtfsStop,"GTFS stop null, this is not allowed");
    PlanItRunTimeException.throwIfNull(primaryMode,"GTFS stop's associated PLANit mode null, this is not allowed");
    PlanItRunTimeException.throwIfNullOrEmpty(nearbyTransferZones,"No nearby transfer zones provided, this is not allowed");

    final boolean stopLocationDirectionSpecific = !(primaryMode.getPhysicalFeatures().getTrackType() == TrackModeType.RAIL);

    var allEligibleModes = data.expandWithCompatibleModes(primaryMode);
    // prune transfer zones
    {
      /* remove nearby zones that are not mode compatible */
      nearbyTransferZones.removeIf(tz -> data.getSupportedPtModesIn(tz, allEligibleModes).isEmpty());
      if(nearbyTransferZones.isEmpty()){
        return null;
      }

      /* remove transfer zones not on the correct side of the road, where rail is acceptable in both directions always */
      nearbyTransferZones.removeIf(
          tz -> stopLocationDirectionSpecific && !GtfsTransferZoneHelper.isGtfsStopOnCorrectSideOfTransferZoneAccessLinkSegments(gtfsStop, primaryMode, tz, data));
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
    var nearbyLinks = GtfsLinkHelper.findNearbyLinks(gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToLinkSearchRadiusMeters(), data);
    Pair<MacroscopicLink, Set<LinkSegment>> accessResult = findMostAppropriateStopLocationLinkForGtfsStop(gtfsStop, allEligibleModes, nearbyLinks);
    if(accessResult == null){
      // todo -> when close to bounding box this might happen (and we either should include this link partly, or not log this warning warning (or both)
      LOGGER.severe(String.format("No appropriate stop location on the network could be found for GTFS stop %s",gtfsStop.getStopId()));
      return null;
    }

    /* try to match based on preferred access link segments versus used access link segments by existing nearby transfer zones ensure that actual access node is within acceptable
     * distance as well, e.g., if too far away the access link segment is too long and transfer zone considered not close enough, given it is a match on the link segment, we allow for a little more distance,
     * namely the allowed distance to a transfer zone + the allowed distance from transfer zone(stop) to the road */
    final var maxStopToAccessNodeDistanceMeters = data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters() + data.getSettings().getGtfsStopToLinkSearchRadiusMeters();
    var matchedTransferZoneAndConnectoid = matchByAccessLinkSegments(gtfsStop, accessResult.second(), nearbyTransferZones, maxStopToAccessNodeDistanceMeters);
    matchedTransferZone = matchedTransferZoneAndConnectoid.first();
    if(matchedTransferZone != null){
      data.getProfiler().incrementMatchedTransferZonesOnAccessLinkSegment();
      return matchedTransferZone;
    }

    /* try to match based on closeness and an acceptable angle difference between a virtual transferzone-to-road-line and virtual GTFS stop-to-road line */
    final double maxAngleDegrees = 100;
    matchedTransferZone = matchByClosestWithAcceptableAccessAngle(gtfsStop, primaryMode, accessResult.second(), nearbyTransferZones, maxAngleDegrees, maxStopToAccessNodeDistanceMeters);

    /* when non rail mode, we would expect only a single mapping, log info when we find multiple to let user verify*/
    if (data.hasMappedGtfsStop(matchedTransferZone) && !(primaryMode.getPhysicalFeatures().getTrackType() == TrackModeType.RAIL)) {
      var earlierMappedStop = data.getMappedGtfsStop(GtfsTransferZoneHelper.getLastTransferZoneExternalId(matchedTransferZone));
      LOGGER.warning(String.format("PLANit transfer zone (%s) is already mapped to another GTFS stop (%s, %s, %s), found additional mapping for GTFS STOP (%s, %s, %s), verify correctness",
          matchedTransferZone.getXmlId(), earlierMappedStop.getStopId(), earlierMappedStop.getStopName(), earlierMappedStop.getLocationAsCoord().toString(), gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord().toString()));
    }
    return matchedTransferZone;
  }

  /**
   * Process a GTFS stop that could not be matched to an existing transfer zone. It will trigger the
   * creation of a new transfer zone on the PLANit zoning as along as it falls within the network's bounding box
   *
   * @param gtfsStop to create new TransferZone for
   * @param primaryGtfsStopModes primary PLANit modes associated with GTFS stop
   * @param type of the to be created TransferZone
   * @return created transfer zone (if any, may be null if not found)
   */
  private TransferZone createNewTransferZoneAndConnectoids(GtfsStop gtfsStop, final List<Mode> primaryGtfsStopModes, TransferZoneType type) {
    PlanItRunTimeException.throwIfNull(gtfsStop,"GTFS stop null, this is not allowed");
    PlanItRunTimeException.throwIfNull(primaryGtfsStopModes,"GTFS stop's associated PLANit mode(s) is/are null, this is not allowed");

    /* check if within network bounding box, only GTFS stops within the network area are considered */
    var projectedGtfsStopLocation = (Point) PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(),data.getCrsTransform());
    if(!data.getReferenceNetworkBoundingBox().contains(projectedGtfsStopLocation.getCoordinate())){
      return null;
    }

    var nearbyLinks = GtfsLinkHelper.findNearbyLinks(
        gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToLinkSearchRadiusMeters(), data);
    if(nearbyLinks.isEmpty() || nearbyLinks == null){
      return null;
    }

//    if(gtfsStopModes.size()>1){
//      int bla = 4;
//    }

    TransferZone newTransferZone = null;
    /* for each mode obtain the necessary information to create connectoid (if deemed valid) */
    for(var gtfsStopMode : primaryGtfsStopModes){
      /* make sure we consider all eligible modes compatible with the primary GTFS stop mode when identifying possible stop locations */
      var allEligibleModes = data.expandWithCompatibleModes(gtfsStopMode);

      /* preferred access link segment for GTFS stop-mode combination */
      var accessResult = findMostAppropriateStopLocationLinkForGtfsStop(gtfsStop, allEligibleModes, nearbyLinks);
      if(accessResult == null){
        final double maxDistanceFromBoundingBoxForDebugMessage = 100; //meters
        if(!data.getGeoTools().isGeometryNearBoundingBox(projectedGtfsStopLocation, data.getReferenceNetworkBoundingBox(), maxDistanceFromBoundingBoxForDebugMessage)) {
          LOGGER.fine(String.format("DISCARD: No nearby links to attach GTFS stop %s %s at location %s [mode %s]", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), gtfsStopMode.getName()));
        }
        continue;
      }

      /* find connectoid location for first acceptable stop-mode combination */
      Point connectoidLocation = null;
      for(var currEligibleMode : allEligibleModes) {
        connectoidLocation = ZoningConverterUtils.findConnectoidLocationForWaitingAreaOnLink(
            gtfsStop.getStopId(), projectedGtfsStopLocation, accessResult.first() /* access link*/, accessResult.first().getExternalId(),
            currEligibleMode,
            data.getSettings().getGtfsStopToLinkSearchRadiusMeters(),null, null, null,
            data.getSettings().getCountryName(), data.getGeoTools());
        if (connectoidLocation != null) {
          break;
        }
      }
      if(connectoidLocation == null){
        LOGGER.warning(String.format("DISCARD: No connectoid location could be found for GTFS stop's %s %s %s selected access link [mode %s], should not happen",gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), gtfsStopMode.getName()));
        continue;
      }

      /* create access node and break links if needed */
      var networkLayer = data.getServiceNetwork().getParentNetwork().getLayerByMode(gtfsStopMode);
      var accessNodeResult = GtfsLinkHelper.extractNodeByLinkGeometryLocation(connectoidLocation, accessResult.first() /* access link */, networkLayer, data);
      Node accessNode = accessNodeResult.first();
      Boolean newlyCreatedAccessNode = accessNodeResult.second();
      if(newlyCreatedAccessNode == true){
        /* link is broken, so we need to update the reference access link and link segments accordingly as the original one is no longer valid */
        accessResult = findMostAppropriateStopLocationLinkForGtfsStop(gtfsStop, allEligibleModes, accessNode.getLinks());
        if(accessResult == null){
          LOGGER.severe(String.format("Unable to update GTFS Stop %s %s %s access link after breaking PLANit link [mode %s], should not happen", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), gtfsStopMode.getName()));
          continue;
        }
      }

      /* some valid connectoid found --> proceed

      /* register new transfer zone if not done already */
      if(newTransferZone == null) {
        newTransferZone = GtfsTransferZoneHelper.createAndRegisterNewTransferZone(gtfsStop, projectedGtfsStopLocation, type, data);
        if (data.getSettings().isLogCreatedGtfsZones()) {
          LOGGER.info(String.format(
              "GTFS stop %s %s at location %s triggered creation of new PLANit Transfer zone %s %s", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord().toString(), newTransferZone.getXmlId(), newTransferZone.hasName() ? newTransferZone.getName() : ""));
        }
      }

      /* register connectoid for each stop-mode combinations selected access link segment on PLANit network/zoning */
      final var finalAccessResult = accessResult;
      allEligibleModes.removeIf( m -> finalAccessResult.second().stream().anyMatch(ls -> !ls.isModeAllowed(m))); // only retain those that also are supported on the found access link segment
      GtfsDirectedConnectoidHelper.createAndRegisterDirectedConnectoids(newTransferZone, networkLayer, finalAccessResult.second(), allEligibleModes, data);
    }

    return newTransferZone;
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

    // set transfer zone name to GTFS stop name if it has no name yet, or GTFS name is longer which is assumed to be more descriptive than PLANit (except when there exist platform names because
    // in that case it is more likely that the existing data is already sufficiently detailed and GTFS stop description might only partially describe the (multi-platform) stop.
    if(!transferZone.hasPlatformNames() && (!transferZone.hasName() || gtfsStop.hasStopName() &&  transferZone.getName().length() < gtfsStop.getStopName().length())){
      transferZone.setName(gtfsStop.getStopName());
    }

    /* supplement platform information if any is present on GTFS but not on transfer zone so far */
    if(gtfsStop.hasPlatformCode()){
      transferZone.addTransferZonePlatformName(gtfsStop.getPlatformCode());
    }

    /* update tracking data */
    data.registerMappedGtfsStop(gtfsStop, transferZone);
    if(data.getSettings().isLogMappedGtfsZones()) {
      LOGGER.info(String.format("Mapped GTFS stop %s %s at location %s to existing Transfer zone %s %s", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord().toString(), transferZone.getXmlId(), transferZone.hasName() ? transferZone.getName() : ""));
    }
  }

  /**
   * Process the GTFS stop which is marked as stop platform and fuse it with the PLANit memory model and previously parsed GTFS entities
   *
   * @param gtfsStop     GTFS stop to processs
   * @param primaryGtfsStopModes identified mode(s) for GTFS stop
   */
  private void handleStopPlatform(final GtfsStop gtfsStop, final List<Mode> primaryGtfsStopModes) {
    data.getProfiler().incrementCount(GtfsObjectType.STOP);

    if(gtfsStop.getStopId().equals("49024")){
      int bla = 4;
    }

    Collection<TransferZone> nearbyTransferZones = GtfsTransferZoneHelper.findNearbyTransferZones(
        gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters(), data);

    TransferZone theTransferZone = null;
    if (!nearbyTransferZones.isEmpty()) {
      for (var primaryMode : primaryGtfsStopModes) {
          var modeTransferZone = findMatchingExistingTransferZone(gtfsStop, primaryMode, nearbyTransferZones);
          if (theTransferZone != null && theTransferZone != modeTransferZone) {
            throw new PlanItRunTimeException("GTFS stop %s %s (location %s) supports multiple modes, but could not map those to a single transfer zone, this shouldn't happen, verify correctness", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord());
          }
          theTransferZone = modeTransferZone;
      }
      if (theTransferZone == null) {
        LOGGER.warning(String.format(
            "GTFS stop %s %s (location %s) [mode(s): %s] has nearby existing transfer zones but no appropriate mapping could be found, verify correctness", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), primaryGtfsStopModes.stream().map(e -> e.getName()).collect(Collectors.joining())));
        return;
      }
    }

    boolean createNewTransferZone = theTransferZone == null;
    if(createNewTransferZone) {
      theTransferZone = createNewTransferZoneAndConnectoids(gtfsStop, primaryGtfsStopModes, TransferZoneType.PLATFORM);
    }else{
      // todo --> move to separate method ugly now but needed to make sure that we add all compatible modes
      for(var connectoid : data.getTransferZoneConnectoids(theTransferZone)){
        for(var primaryMode : primaryGtfsStopModes) {
          var allEligibleModes = data.expandWithCompatibleModes(primaryMode);
          // todo --> place breakpoint here and see that first hit results in lightrail/tram situation where
          // todo     the tram mode would be added but the link segment's type does not allow tram, only lightrail --> yet
          // todo     looking at the network this should also support tram (it is more tram than lightrail
          // todo     Figure out if we need to adjust the OSM reader + GTFS reader to adjust link segment types in both
          // todo     in GTFS reader, likely we need to update the link segment types based on the compatible modes during configuration and log this to user!!
          allEligibleModes.removeIf(m -> !connectoid.getAccessLinkSegment().isModeAllowed(m));
          connectoid.addAllowedModes(theTransferZone, allEligibleModes);
          data.registerTransferZoneToConnectoidModes(theTransferZone, connectoid, allEligibleModes);
        }
      }
    }

    if(theTransferZone!= null){
      attachToTransferZone(gtfsStop, theTransferZone);
      if(!createNewTransferZone){
        data.getProfiler().incrementAugmentedTransferZones();
      }
    }

  }

  /**
   * Process manually overwritten mapping between GTFS stop and existing PLANit transfer zone
   *
   * @param gtfsStop to process
   */
  private void handleOverwrittenTransferZoneMapping(GtfsStop gtfsStop) {
    String transferZoneExternalId = data.getSettings().getOverwrittenGtfsStopTransferZoneMapping(gtfsStop.getStopId());
    var transferZone = data.getPreExistingTransferZonesByExternalId().get(transferZoneExternalId);
    if(transferZone == null){
      LOGGER.warning(String.format("GTFS stop %s %s (location %s) manually attached to existing transfer zone %s, but transfer zone not found, ignored",gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), transferZoneExternalId));
      return;
    }

    attachToTransferZone(gtfsStop, transferZone);
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

//    if(gtfsStop.getStopId().equals("4192")){
//      int bla = 4;
//    }
    final List<Mode> gtfsStopModes = data.getSupportedPtModes(gtfsStop);
    if(gtfsStopModes == null){
      return;
    }
    final var activatedModes = data.getActivatedPlanitModesByGtfsMode();
    gtfsStopModes.removeIf(m -> !activatedModes.contains(m));
    if(gtfsStopModes.isEmpty()){
      return;
    }

    if(data.getSettings().isOverwrittenGtfsStopTransferZoneMapping(gtfsStop.getStopId())){
      handleOverwrittenTransferZoneMapping(gtfsStop);
      return;
    }

    switch (gtfsStop.getLocationType()){
      case STOP_PLATFORM:
        handleStopPlatform(gtfsStop, gtfsStopModes);
        return;
      case BOARDING_AREA:
        // not processed yet, if we find that boarding areas are used without a platform, they could be treated as a platform
        return;
      case STATION:
        // not processed yet
        return;
      case GENERIC_NODE:
        // not processed yet
        return;
      case ENTRANCE_EXIT:
        // not processed yet, in future these could be used to connect to a separate pedestrian layer but this is not yet available
        return;
      default:
        throw new PlanItRunTimeException("Unrecognised GTFS stop location type %s encountered", gtfsStop.getLocationType());
    }
  }

}

