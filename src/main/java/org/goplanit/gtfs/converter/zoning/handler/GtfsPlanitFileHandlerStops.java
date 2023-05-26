package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.converter.idmapping.IdMapperFunctionFactory;
import org.goplanit.converter.idmapping.IdMapperType;
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
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.TrackModeType;
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
   * Based on GTFS stop provide eligible links for mapping based on proximity or manually overwritten alternative(s)
   *
   * @param gtfsStop                  to get eligible links for
   * @param projectedGtfsStopLocation of the GTFS stop
   * @param mode2EligibleModesMapping to consider
   * @return found nearby or overwritten link(s) as well as closest of those options
   */
  private Pair<Collection<MacroscopicLink>, MacroscopicLink> findEligibleLinkMappings(
      GtfsStop gtfsStop, Point projectedGtfsStopLocation, SortedMap<Mode, SortedSet<Mode>> mode2EligibleModesMapping) {

    MacroscopicLink closestOfNearbyLinks = null;
    Collection<MacroscopicLink> nearbyLinks = null;

    /* USER OVERWRITTEN */
    if(data.getSettings().hasOverwrittenGtfsStopToLinkMapping(gtfsStop.getStopId())){
      var linkIdMapping = data.getSettings().getOverwrittenGtfsStopToLinkMapping(gtfsStop.getStopId());
      var chosenLinkId = linkIdMapping.first();
      var idMapper = IdMapperFunctionFactory.createLinkIdMappingFunction(linkIdMapping.second());

      /* check mode compatible layers for existence of the link */
      for(var gtfsStopModeEntry : mode2EligibleModesMapping.entrySet()) {
        SortedSet<Mode> allEligibleModes = gtfsStopModeEntry.getValue();
        for(var eligibleMode : allEligibleModes){
          var foundLink = data.getServiceNetwork().getParentNetwork().getLayerByMode(eligibleMode).getLinks().firstMatch(
              l -> chosenLinkId.equals(idMapper.apply(l)));
          if(foundLink != null){
            closestOfNearbyLinks = foundLink;
            nearbyLinks = Collections.singleton(closestOfNearbyLinks);
            break;
          }
        }
      }

      if(closestOfNearbyLinks == null){
        LOGGER.warning(String.format("Unable to find manually overwritten link mapping for GTFS stop id %s in network, instead trying to map as if it is a regular GTFS stop, verify settings", gtfsStop.getStopId()));
      }
    }

    /* REGULAR - BASED ON PROXIMITY ONLY */
    if(closestOfNearbyLinks == null){
      // nearby links that are mode compatible with any of the eligible modes across all primary modes
      nearbyLinks = GtfsLinkHelper.findNearbyLinks(
          gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToLinkSearchRadiusMeters(), data);
      nearbyLinks.removeIf(l -> !mode2EligibleModesMapping.values().stream().flatMap(v -> v.stream()).anyMatch(m -> l.isModeAllowedOnAnySegment(m)));
      if (nearbyLinks.isEmpty() || nearbyLinks == null) {
        return null;
      }
      closestOfNearbyLinks = PlanitEntityGeoUtils.findPlanitEntityClosest(projectedGtfsStopLocation.getCoordinate(), nearbyLinks, data.getGeoTools()).first();
    }

    return Pair.of(nearbyLinks, closestOfNearbyLinks);
  }

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
      GtfsStop gtfsStop, final Collection<Mode> eligibleAccessModes, final Collection<MacroscopicLink> eligibleLinks) {
    final Point projectedGtfsStopLocation = (Point) PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());

    Function<MacroscopicLink, String> linkToSourceId = l -> l.getExternalId(); // make configurable as used for overwrites

    /* 1) reduce candidates to access links related to access link segments that are deemed valid in terms of mode and location (closest already complies as per above) */
    Set<LinkSegment> accessLinkSegments = new HashSet<>(2);
    for(var currAccessLink : eligibleLinks) {
      for(var accessMode : eligibleAccessModes) {
        boolean mustAvoidCrossingTraffic = ZoningConverterUtils.isAvoidCrossTrafficForAccessMode(accessMode);
        var currAccessLinkSegments = ZoningConverterUtils.findAccessLinkSegmentsForWaitingArea(
            gtfsStop.getStopId(),
            projectedGtfsStopLocation,
            currAccessLink,
            linkToSourceId.apply(currAccessLink),
            accessMode,
            data.getSettings().getCountryName(),
            mustAvoidCrossingTraffic, null, null, data.getGeoTools());
        if (currAccessLinkSegments != null && !currAccessLinkSegments.isEmpty()) {
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

     /* 3) all proper candidates so  reduce options further based on proximity to closest viable link, while removing options outside the closest distance buffer */
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
     * so, we choose the first link segment with the highest capacity found (if they differ) and then return its parent link as the candidate */
    if( eligibleAccessModes.stream().findAny().get().getPhysicalFeatures().getTrackType() == TrackModeType.ROAD &&
        filteredCandidates.size()>1 &&
        filteredCandidates.stream().flatMap(l -> l.getLinkSegments().stream()).filter(ls -> accessLinkSegments.contains(ls)).map(
            ls -> ls.getCapacityOrDefaultPcuHLane()).distinct().count()>1){

      // retain only edge segments with the maximum capacity
      var maxCapacity = accessLinkSegments.stream().map(ls -> ((MacroscopicLinkSegment)ls).getCapacityOrDefaultPcuHLane()).max(Comparator.naturalOrder());
      var lowerCapacitySegments = filteredCandidates.stream().flatMap(l -> l.getLinkSegments().stream()).filter(ls -> Precision.smaller( ls.getCapacityOrDefaultPcuHLane(), maxCapacity.get(), Precision.EPSILON_6)).collect(Collectors.toUnmodifiableSet());
      accessLinkSegments.removeAll(lowerCapacitySegments);
      filteredCandidates.removeAll(lowerCapacitySegments.stream().map(ls -> ls .getParentLink()).collect(Collectors.toUnmodifiableSet()));
    }

    /* now find the closest remaining*/
    MacroscopicLink finalSelectedAccessLink = filteredCandidates.iterator().next();
    if(filteredCandidates.size()>1) {
      finalSelectedAccessLink = (MacroscopicLink) PlanitGraphGeoUtils.findEdgeClosest(projectedGtfsStopLocation, filteredCandidates, data.getGeoTools());
    }
    final var dummy = finalSelectedAccessLink;
    accessLinkSegments.removeIf(ls -> !ls.getParent().equals(dummy)); // sync

    return Pair.of(finalSelectedAccessLink, accessLinkSegments);
  }


  /**
   * Match first transfer zone in collection which has a platform name that equals the GTFS stops platform code
   *
   * @param gtfsStop         to match against
   * @param transferZones    to check against
   * @param allEligibleModes eligble modes for mathcing
   * @return found transfer zone, null if none found
   */
  private TransferZone matchByPlatform(GtfsStop gtfsStop, Collection<TransferZone> transferZones, SortedSet<Mode> allEligibleModes) {
    /* platform codes should only be present when platform is part of a station */
    if(!gtfsStop.hasPlatformCode()){
      return null;
    }

    /* find transfer zone with first matching platform name */
    for(var transferZone : transferZones){
      var platformNames = transferZone.getTransferZonePlatformNames();
      if(platformNames != null &&
          platformNames.stream().filter( name -> name.equalsIgnoreCase(gtfsStop.getPlatformCode())).findFirst().isPresent() &&
          !data.getSupportedPtModesIn(transferZone, allEligibleModes).isEmpty()){
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
              Function<TransferZone, String> lambda = (tz) -> "(" + tz.getXmlId() + ", name: " + tz.getName() + ", ext id: "+ tz.getExternalId() + ")";
              LOGGER.warning(
                  String.format("Multiple transfer zones [%s] with access link segment (%s, ext id: %s) matched to GTFS stop %s %s (location %s), choosing first",
                      lambda.apply(match) + ", " + lambda.apply(transferZone), cn.getAccessLinkSegment().getXmlId(), cn.getAccessLinkSegment().getParentLink().getExternalId(), gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord()));
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
        if(!cn.getAccessLinkSegment().isModeAllowed(gtfsStopMode)){
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
          "GTFS stop %s %s (location %s) mapped to transfer zone (%s, ext id:%s, name: %s), but GTFS preferred access link segment (XmlId: %s, ExtId: %s) not adjacent to existing access link segments, verify correctness",
          gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), matchedTransferZone.getXmlId(), matchedTransferZone.getExternalId(), matchedTransferZone.getName(), accessLinkSegment.getXmlId(), accessLinkSegment.getParentLink().getExternalId()));
    }

    return matchedTransferZone;
  }

  /**
   * Process a GTFS stop that is may be matchable to nearby existing transfer zone(s).
   * If a match is found it will provide the match, of not null is returned and a warning may be logged if appropriate
   *
   * @param gtfsStop      to create new TransferZone for
   * @param primaryGtfsStopModes PLANit modes associated with GTFS stop
   * @return found match (not attached yet), null if no match is found, the mode can be the primary mode initially provided, or a compatible alternative mode
   *          that is deemed a valid alternative. If the latter is the case, the found transfer zone is not compatible with the primary mode
   */
  private TransferZone findMatchingExistingTransferZone(GtfsStop gtfsStop, final List<Mode> primaryGtfsStopModes) {
    PlanItRunTimeException.throwIfNull(gtfsStop,"GTFS stop null, this is not allowed");

    Collection<TransferZone> nearbyTransferZones = GtfsTransferZoneHelper.findNearbyTransferZones(
        gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters(), data);
    if (nearbyTransferZones.isEmpty()) {
      return null;
    }

    TransferZone theTransferZone = null;
    for (var primaryMode : primaryGtfsStopModes) {

      var consideredTransferZones = new ArrayList<>(nearbyTransferZones);
      final boolean stopLocationDirectionSpecific = ZoningConverterUtils.isAvoidCrossTrafficForAccessMode(primaryMode);

      var allEligibleModes = data.expandWithCompatibleModes(primaryMode);

      // PRUNE STANDARD
      // prune transfer zones based on attributes that if nothing is left there is no need to inform the user
      // we can safely create new transfer zones as it is unlikely pruning is erroneous
      {
        /* remove nearby zones that are not mode compatible */
        consideredTransferZones.removeIf(tz -> data.getSupportedPtModesIn(tz, allEligibleModes).isEmpty());
        if (consideredTransferZones.isEmpty()) {
          continue;
        }

        /* remove transfer zones if no connectoids are found that reside on the correct side of the road, where rail is acceptable in both directions always*/
        consideredTransferZones.removeIf(
            tz -> stopLocationDirectionSpecific &&
                !GtfsTransferZoneHelper.isGtfsStopOnCorrectSideOfTransferZoneAccessLinkSegments(gtfsStop, primaryMode, tz, data, false));
        if (consideredTransferZones.isEmpty()) {
          continue;
        }

        /* remove transfer zones that already have a mapping to a GTFS stop when user has indicated no joint mappings are allowed */
        if (data.getSettings().isDisallowGtfsStopToTransferZoneJointMapping(gtfsStop.getStopId())) {
          consideredTransferZones.removeIf(tz -> data.hasMappedGtfsStop(tz));
        }
      }

      var modeTransferZone = findMatchingExistingTransferZoneByPlatformOrLinks(gtfsStop, primaryMode, nearbyTransferZones);
      if (theTransferZone != null && theTransferZone != modeTransferZone) {
        throw new PlanItRunTimeException("GTFS stop %s %s (location %s) supports multiple modes, but could not map those to a single transfer zone, this shouldn't happen, verify correctness", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord());
      }
      theTransferZone = modeTransferZone;
    }

    if (theTransferZone == null) {
      LOGGER.fine(String.format(
          "GTFS stop %s %s (location %s) [mode(s): %s] not matched to nearby transfer zone(s) (%s), creating new transfer zone",
          gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), primaryGtfsStopModes.stream().map(e -> e.getName()).collect(Collectors.joining()),
          nearbyTransferZones.stream().map(tz -> "[" + tz.getXmlId() + ", name: " + tz.getName() + ", ext id: " + tz.getExternalId() + "]").collect(Collectors.joining())));
    }
    return theTransferZone;
  }

  /**
   * Process a GTFS stop that is expected to be matchable to nearby existing transfer zone(s).
   * If a match is found it will provide the match, of not null is returned and a warning is logged to the user if appropriate
   *
   * @param gtfsStop      to create new TransferZone for
   * @param primaryMode PLANit mode associated with GTFS stop
   * @param nearbyTransferZones to consider, note this container will be pruned if zones are not eligible
   * @return found match (not attached yet), null if no match is found, the mode can be the primary mode initially provided, or a compatible alternative mode
   *          that is deemed a valid alternative. If the latter is the case, the found transfer zone is not compatible with the primary mode
   */
  private TransferZone findMatchingExistingTransferZoneByPlatformOrLinks(final GtfsStop gtfsStop, final Mode primaryMode, final Collection<TransferZone> nearbyTransferZones) {
    PlanItRunTimeException.throwIfNull(primaryMode,"GTFS stop's associated PLANit mode null, this is not allowed");
    PlanItRunTimeException.throwIfNullOrEmpty(nearbyTransferZones,"No nearby transfer zones provided, this is not allowed");

    var allEligibleModes = data.expandWithCompatibleModes(primaryMode);

    /* try to match on platform name first as it is most trustworthy... */
    var matchedTransferZone = matchByPlatform(gtfsStop, nearbyTransferZones, allEligibleModes);
    if(matchedTransferZone != null){
      data.getProfiler().incrementMatchedTransferZonesOnPlatformName();
      return matchedTransferZone;
    }

    /* identify preferred access link (segments) for GTFS stop as if there were no existing transfer zones to map to, to use for matching */
    var nearbyLinks = GtfsLinkHelper.findNearbyLinks(gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToLinkSearchRadiusMeters(), data);
    if(nearbyLinks == null || nearbyLinks.isEmpty()){
      LOGGER.warning(String.format("No nearby links found for GTFS stop %s within search radius of %.2fm, consider expanding search radius, or override to attach to any of transfer zones: %s",
          gtfsStop.getStopId(), data.getSettings().getGtfsStopToLinkSearchRadiusMeters(), nearbyTransferZones.stream().map(tz -> "[" + tz.getXmlId() + ", name: " + tz.getName() + ", ext id: " + tz.getExternalId() + "]").collect(Collectors.joining())));
      return null;
    }

    /* filter nearby links based on the transfer zone links that are deemed possibly compatible */
    var transferZoneAccessLinks = nearbyTransferZones.stream().flatMap( tz -> data.getTransferZoneConnectoids(tz).stream()).map(c -> (MacroscopicLink)c.getAccessLinkSegment().getParentLink()).collect(Collectors.toSet());
    var transferZoneCompatibleNearbyLinks = transferZoneAccessLinks.stream().filter(nearbyLinks::contains).collect(Collectors.toSet());
    if(transferZoneCompatibleNearbyLinks.isEmpty()){
      /* all nearby transfer zone access links are too far, so unlikely they make sense to use */
      return null;
    }

    Pair<MacroscopicLink, Set<LinkSegment>> accessResult = findMostAppropriateStopLocationLinkForGtfsStop(gtfsStop, allEligibleModes, transferZoneCompatibleNearbyLinks);
    if(accessResult == null){
      LOGGER.info(String.format("GTFS stop (%s %s %s) access Links [%s] of nearby transfer zones %s incompatible/too far, creating new transfer zone instead (unless indicated otherwise) ",
          gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsPoint(), transferZoneCompatibleNearbyLinks.stream().map(l -> l.getIdsAsString()).collect(Collectors.joining(",")), nearbyTransferZones.stream().map(tz -> "[" + tz.getXmlId() + ", name: " + tz.getName() + ", ext id: " + tz.getExternalId() + "]").collect(Collectors.joining())));
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

      /* pinpointed to single link, log if required */
      if(data.getSettings().isLogGtfsStopToLinkMapping(gtfsStop.getStopId())){
        LOGGER.info(String.format("GTFS stop (%s %s %s) mapped to PLANit link (%s) of existing PLANit transfer zone (%s)",gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsPoint(), matchedTransferZoneAndConnectoid.second().getAccessLinkSegment().getParentLink().getIdsAsString(), matchedTransferZone.getIdsAsString()));
      }
      return matchedTransferZone;
    }

    /* try to match based on closeness and an acceptable angle difference between a virtual transferzone-to-road-line and virtual GTFS stop-to-road line */
    final double maxAngleDegrees = 100;
    matchedTransferZone = matchByClosestWithAcceptableAccessAngle(gtfsStop, primaryMode, accessResult.second(), nearbyTransferZones, maxAngleDegrees, maxStopToAccessNodeDistanceMeters);

    /* when road mode, we would expect only a single mapping, log info when we find multiple to let user verify*/
    if (data.hasMappedGtfsStop(matchedTransferZone) && primaryMode.getPhysicalFeatures().getTrackType() == TrackModeType.ROAD) {
      var earlierMappedStop = data.getMappedGtfsStop(GtfsTransferZoneHelper.getLastTransferZoneExternalId(matchedTransferZone));
      LOGGER.warning(String.format("PLANit transfer zone (%s) for GTFS STOP (%s, %s, %s) already mapped to another GTFS stop (%s, %s, %s), consider disallowing joined mapping or force creating a new transfer zone via settings",
          matchedTransferZone.getIdsAsString(), gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord().toString(), earlierMappedStop.getStopId(), earlierMappedStop.getStopName(), earlierMappedStop.getLocationAsCoord().toString()));
    }

    /* pinpointed to transfer zone as a whole, log all connectoid links if required */
    if(data.getSettings().isLogGtfsStopToLinkMapping(gtfsStop.getStopId())){
      var linkIds = data.getTransferZoneConnectoids(matchedTransferZone).stream().map(c -> c.getAccessLinkSegment().getParentLink()).distinct().map( l -> l.getIdsAsString()).collect(Collectors.joining(","));
      LOGGER.info(String.format("GTFS stop (%s %s %s) mapped to all eligible PLANit link(s) [%s] of existing PLANit transfer zone %s",gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsPoint(), linkIds, matchedTransferZone.getIdsAsString()));
    }

    return matchedTransferZone;
  }

  /**
   * Process a GTFS stop that could not be matched to an existing transfer zone. It will trigger the
   * creation of a new transfer zone on the PLANit zoning as along as it falls within the network's bounding box and resides within acceptable
   * distance of PLANit network links
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

    if(gtfsStop.getStopId().equals("200018")){
      int bla = 4;
    }

    SortedMap<Mode, SortedSet<Mode>> mode2EligibleModesMapping = new TreeMap<>();
    primaryGtfsStopModes.forEach( m -> mode2EligibleModesMapping.put(m, data.expandWithCompatibleModes(m)));

    var eligibleLinksAndClosest = findEligibleLinkMappings(gtfsStop, projectedGtfsStopLocation, mode2EligibleModesMapping);
    if(eligibleLinksAndClosest == null){
      return null;
    }
    MacroscopicLink closestOfNearbyLinks = eligibleLinksAndClosest.second();
    Collection<MacroscopicLink> nearbyLinks = eligibleLinksAndClosest.first();

    TransferZone newTransferZone = null;
    boolean connectoidsCreated = false;
    /* for each mode obtain the necessary information to create connectoid (if deemed valid) */
    for(var gtfsStopMode : primaryGtfsStopModes){
      /* make sure we consider all eligible modes compatible with the primary GTFS stop mode when identifying possible stop locations */
      SortedSet<Mode> allEligibleModes = mode2EligibleModesMapping.get(gtfsStopMode);

      /* preferred access link segment for GTFS stop-mode combination */
      var accessResult = findMostAppropriateStopLocationLinkForGtfsStop(gtfsStop, allEligibleModes, nearbyLinks);
      if(accessResult == null){
        continue;
      }

      /* inform user if found location resides on link that is not closest to the GTFS stop, as this might pinpoint to the GTFS stop being located on - for example
       *  the wrong side of the road, in which case it should be overwritten by the user (which is only possible if they are provided with the information to do so */
      boolean chosenNonClosestLink = false;
      //var distanceToChosenLink = PlanitEntityGeoUtils.getDistanceToEdge(projectedGtfsStopLocation.getCoordinate(), accessResult.first(), data.getGeoTools());
      if(!closestOfNearbyLinks.equals(accessResult.first())){
        chosenNonClosestLink = true; // postpone warning until we know
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

      /* some potentially valid connectoid found --> proceed */
      if(chosenNonClosestLink){
        LOGGER.warning(String.format("GTFS Stop %s %s %s [mode %s] chosen access link (%s %s) is not the closest link (%s, external id: %s), GTFS stop may be tagged in wrong location, verify correctness",
            gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), gtfsStopMode.getName(), accessResult.first().getName(), accessResult.first().getIdsAsString(), closestOfNearbyLinks.getXmlId(), closestOfNearbyLinks.getExternalId()));
      }

      /* create access node and break links if needed */
      var networkLayer = data.getServiceNetwork().getParentNetwork().getLayerByMode(gtfsStopMode);
      var accessNodeResult = GtfsLinkHelper.extractNodeByLinkGeometryLocation(connectoidLocation, accessResult.first() /* access link */, networkLayer, data);
      Node accessNode = accessNodeResult.first();

      /* with access node known, now find all access segments that are eligible */
      final boolean mustAvoidCrossingTraffic =  ZoningConverterUtils.isAvoidCrossTrafficForAccessMode(gtfsStopMode);
      Collection<LinkSegment> accessLinkSegments = null;
      for(MacroscopicLink link : accessNode.<MacroscopicLink>getLinks()) {
        Collection<LinkSegment> linkAccessLinkSegments = ZoningConverterUtils.findAccessLinkSegmentsForWaitingArea(
            gtfsStop.getStopId(),
            projectedGtfsStopLocation,
            link,
            link.getExternalId(),
            accessNode,
            gtfsStopMode,
            data.getSettings().getCountryName(),
            mustAvoidCrossingTraffic,
            null,
            null,
            data.getGeoTools());
        if(linkAccessLinkSegments != null && !linkAccessLinkSegments.isEmpty()) {
          if(accessLinkSegments == null) {
            accessLinkSegments = linkAccessLinkSegments;
          }else {
            accessLinkSegments.addAll(linkAccessLinkSegments);
          }
        }
      }

      /* register new transfer zone if not done already */
      if(newTransferZone == null) {
        newTransferZone = GtfsTransferZoneHelper.createAndRegisterNewTransferZone(gtfsStop, projectedGtfsStopLocation, type, data);
        if (data.getSettings().isLogCreatedGtfsZones()) {
          LOGGER.info(String.format(
              "GTFS stop %s %s at location %s triggered creation of new PLANit Transfer zone %s %s", gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord().toString(), newTransferZone.getXmlId(), newTransferZone.hasName() ? newTransferZone.getName() : ""));
        }
      }

      /* register connectoid for each stop-mode combinations selected access link segment on PLANit network/zoning */
      final var finalAccessLinkSegments = accessLinkSegments;
      allEligibleModes.removeIf( m -> finalAccessLinkSegments.stream().anyMatch(ls -> !ls.isModeAllowed(m))); // only retain those that also are supported on the found access link segment
      var results = GtfsDirectedConnectoidHelper.createAndRegisterDirectedConnectoids(
          newTransferZone, networkLayer, accessNode, accessLinkSegments, allEligibleModes, data);
      connectoidsCreated = connectoidsCreated || results != null && !results.isEmpty();
    }

    final double maxDistanceFromBoundingBoxForDebugMessage = 100; //meters
    if(newTransferZone==null && !data.getGeoTools().isGeometryNearBoundingBox(projectedGtfsStopLocation, data.getReferenceNetworkBoundingBox(), maxDistanceFromBoundingBoxForDebugMessage)) {
      LOGGER.warning(String.format("DISCARD: GTFS stop (%s %s location %s [mode(s) %s]) nearby available links [%s] incompatible/too far - GTFS stop resides near bounding box, or possible tagging mismatch, verify GTFS stop does not reside on wrong side of underlying road network",
          gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), primaryGtfsStopModes.stream().map(m -> m.toString()).collect(Collectors.joining(",")), nearbyLinks.stream().map(l -> l.getIdsAsString()).collect(Collectors.joining(","))));
    }
    if(newTransferZone != null && !connectoidsCreated){
      LOGGER.severe(String.format(" Transfer zone created for GTFS stop %s %s location %s [mode(s) %s] but no connection to the physical network was established, this shouldn't happen",
          gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), primaryGtfsStopModes.stream().map(m -> m.toString()).collect(Collectors.joining(","))));
    }

    if(data.getSettings().isLogGtfsStopToLinkMapping(gtfsStop.getStopId()) && connectoidsCreated ){
      var linkIds = data.getTransferZoneConnectoids(newTransferZone).stream().map(c -> c.getAccessLinkSegment().getParentLink()).distinct().map( l -> l.getIdsAsString()).collect(Collectors.joining(","));
      LOGGER.info(String.format("GTFS stop (%s %s %s) mapped to PLANit link(s) [%s] - new PLANit transfer zone (%s)",gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsPoint(), linkIds, newTransferZone.getIdsAsString()));
    }

    return newTransferZone;
  }

  /* Update connectoid mode support to secondary modes for the GTFS stop's primary modes that we matched to this transfer zone. Useful
   *  in case the primary mode of GTFS is not supported by the physical network (but the secondary one is)
   *
   * @param theTransferZone to use
   * @param primaryGtfsStopModes gtfs stop's primary supported modes to base all elgigible modes on
   */
  private void updateTransferZoneConnectoidSecondaryCompatibleModes(TransferZone theTransferZone, List<Mode> primaryGtfsStopModes) {
    for(var connectoid : data.getTransferZoneConnectoids(theTransferZone)){
      for(var primaryMode : primaryGtfsStopModes) {
        var allEligibleModes = data.expandWithCompatibleModes(primaryMode);
        /* add support for all (secondary) modes that are also supported by the access link segment of the connectoid */
        allEligibleModes.removeIf(m -> !connectoid.getAccessLinkSegment().isModeAllowed(m));
        connectoid.addAllowedModes(theTransferZone, allEligibleModes);
        data.registerTransferZoneToConnectoidModes(theTransferZone, connectoid, allEligibleModes);
      }
    }
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

    if(gtfsStop.getStopId().equals("20002")){
      int bla = 4;
    }

    TransferZone theTransferZone = null;
    if(!data.getSettings().isForceCreateNewTransferZoneForGtfsStops(gtfsStop.getStopId())) {

      theTransferZone = findMatchingExistingTransferZone(gtfsStop, primaryGtfsStopModes);

    }

    boolean createNewTransferZone = theTransferZone == null;
    if(createNewTransferZone) {
      theTransferZone = createNewTransferZoneAndConnectoids(gtfsStop, primaryGtfsStopModes, TransferZoneType.PLATFORM);
    }else{
      /* make sure connectoid mode support is updated to secondary modes for the GTFS stop that we matched to this transfer zone
      *  needed in case the primary mode of GTFS is not supported by the physical network (but the secondary one is) */
      updateTransferZoneConnectoidSecondaryCompatibleModes(theTransferZone, primaryGtfsStopModes);
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
   * @param gtfsStop      to process
   * @param primaryGtfsStopModes the primary modes supported by this GTFS stop
   */
  private void handleOverwrittenTransferZoneMapping(GtfsStop gtfsStop, List<Mode> primaryGtfsStopModes) {
    var transferZoneIdAndType = data.getSettings().getOverwrittenGtfsStopTransferZoneMapping(gtfsStop.getStopId());
    TransferZone transferZone = null;
    if(transferZoneIdAndType.second() == IdMapperType.EXTERNAL_ID) {
      transferZone = data.getPreExistingTransferZonesByExternalId().get((String)transferZoneIdAndType.first());
    }else if(transferZoneIdAndType.second() == IdMapperType.ID){
      transferZone = data.getZoning().getTransferZones().get((Integer)transferZoneIdAndType.first());
    }else if(transferZoneIdAndType.second() == IdMapperType.XML){
      transferZone = data.getZoning().getTransferZones().getByXmlId((String)transferZoneIdAndType.first());
    }

    if(transferZone == null){
      LOGGER.warning(
          String.format(
              "GTFS stop %s %s (location %s) manually attached to existing transfer zone (%s, %s), but transfer zone not found, ignored",gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord(), transferZoneIdAndType.first(), transferZoneIdAndType.second()));
      return;
    }

    updateTransferZoneConnectoidSecondaryCompatibleModes(transferZone, primaryGtfsStopModes);
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

    if(this.data.getSettings().isExcludedGtfsStop(gtfsStop.getStopId())){
      return;
    }

    /* GTFS mode compatibility */
    final List<Mode> gtfsStopModes = data.getSupportedPtModes(gtfsStop);
    if(gtfsStopModes == null){
      return;
    }

    /* PLANit mode mapping compatibility */
    final var activatedModes = data.getActivatedPlanitModesByGtfsMode();
    gtfsStopModes.removeIf(m -> !activatedModes.contains(m));
    if(gtfsStopModes.isEmpty()){
      return;
    }

    /* OVERRIDES CHECKING */
    {
      if(data.getSettings().isOverwrittenGtfsStopTransferZoneMapping(gtfsStop.getStopId())){
        handleOverwrittenTransferZoneMapping(gtfsStop, gtfsStopModes);
        return;
      }

      if(data.getSettings().isOverwrittenGtfsStopLocationMapping(gtfsStop.getStopId())){
        gtfsStop.setLocationAsCoord(data.getSettings().getOverwrittenGtfsStopLocation(gtfsStop.getStopId()));
      }
    }

    if(gtfsStop.getStopId().equals("2000457")){
      int bla = 4;
    }

    /* processing */
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

