package org.goplanit.gtfs.converter.zoning.handler;

import org.goplanit.gtfs.entity.GtfsStop;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.handler.GtfsFileHandlerStops;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.GeoContainerUtils;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.CharacterUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.zoning.TransferZone;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

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
      boolean leftOf = data.getGeoTools().isGeometryLeftOf(localProjection, accessSegment.getUpstreamVertex().getPosition().getCoordinate(), accessSegment.getDownstreamVertex().getPosition().getCoordinate());
      if (leftHandDrive != leftOf) {
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
    var searchEnvelope = data.getGeoTools().createBoundingBox(location.getX(),location.getY(),pointSearchRadiusMeters);
    searchEnvelope = PlanitJtsUtils.transformEnvelope(searchEnvelope, data.getCrsTransform());
    return GeoContainerUtils.queryZoneQuadtree(data.getGeoIndexedTransferZones(), searchEnvelope);
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

  /** From the provided options, select the most appropriate based on proximity, mode compatibility, and relative location to Gtfs Stop
   *
   * @param gtfsStop under consideration
   * @param accessMode access mode to use
   * @param eligibleLinks to consider
   * @return most appropriate link that is found
   */
  private Link findMostAppropriateAccessLinkSegmentForGtfsStop(GtfsStop gtfsStop, Mode accessMode, Collection<Link> eligibleLinks) {

    /* filter links by eligible mode */
    final Predicate<Link> modeAllowedOnAnySegment =
        l -> (l.hasLinkSegmentBa() && ((MacroscopicLinkSegment)l.getLinkSegmentBa()).isModeAllowed(accessMode)) || (l.hasLinkSegmentAb() && ((MacroscopicLinkSegment)l.getLinkSegmentAb()).isModeAllowed(accessMode));
    eligibleLinks.removeIf(modeAllowedOnAnySegment.negate());


    //TODO: continue below taken from OSM needs tweaking
//    /* Preprocessing only for user warning:
//     * check if closest road is compatible regarding driving direction (relative location of waiting area versus road)
//     * if not, a user warning is needed in case of possible tagging error regarding closest road (not being valid) for transfer zone, then try to salvage */
//    boolean salvaging = false;
//    do{
//      Link closestLink = (Link) PlanitGraphGeoUtils.findEdgeClosest(transferZone.getGeometry(), eligibleLinks, getGeoUtils());
//      Collection<Link> result = filterDrivingDirectionCompatibleLinks(transferZone.getGeometry(), Collections.singleton(osmAccessMode), Collections.singleton(closestLink));
//      if(result!=null && !result.isEmpty()){
//        /* closest is also viable, continue */
//        break;
//      }else {
//        /* closest link is on the wrong side of the waiting area, let user know, possibly tagging error */
//        LOGGER.fine(String.format("Waiting area (osm id %s) for mode %s is situated on the wrong side of closest eligible road %s, attempting to salvage",
//            transferZone.getExternalId(),osmAccessMode,closestLink.getExternalId()));
//        eligibleLinks.remove(closestLink);
//        salvaging = true;
//      }
//
//      if(eligibleLinks.isEmpty()){
//        break;
//      }
//    }while(true);
//
//    if(eligibleLinks.isEmpty()) {
//      logWarningIfNotNearBoundingBox(
//          String.format("DISCARD: No suitable stop_location on correct side of osm way candidates available for transfer zone %s and mode %s", transferZone.getExternalId(), osmAccessMode), transferZone.getGeometry());
//      return null;
//    }
//
//    /* reduce options based on proximity to closest viable link, without ruling out other options that might also be valid*/
//    Link selectedAccessLink = null;
//    Pair<? extends Edge, Set<? extends Edge>> candidatesForStopLocation = PlanitGraphGeoUtils.findEdgesClosest(
//        transferZone.getGeometry(), eligibleLinks, OsmPublicTransportReaderSettings.DEFAULT_CLOSEST_EDGE_SEARCH_BUFFER_DISTANCE_M, getGeoUtils());
//    if(candidatesForStopLocation==null) {
//      throw new PlanItRunTimeException("No closest link could be found from selection of eligible closeby links when finding stop locations for transfer zone (osm entity id %s), this should not happen", transferZone.getExternalId());
//    }
//
//    if(candidatesForStopLocation.second() == null || candidatesForStopLocation.second().isEmpty() ) {
//      /* only one option */
//      selectedAccessLink = (Link) candidatesForStopLocation.first();
//    }else {
//
//      /* multiple candidates still, filter candidates based on availability of valid stop location checking (mode support, correct location compared to zone etc.) */
//      Mode accessMode = getNetworkToZoningData().getNetworkSettings().getMappedPlanitMode(osmAccessMode);
//      MacroscopicNetworkLayer networkLayer = getSettings().getReferenceNetwork().getLayerByMode(accessMode);
//
//      @SuppressWarnings("unchecked")
//      Set<Link> candidatesToFilter = (Set<Link>) candidatesForStopLocation.second();
//      candidatesToFilter.add((Link)candidatesForStopLocation.first());
//
//      /* 1) reduce options by removing all compatible links within proximity of the closest link that are on the wrong side of the road infrastructure */
//      candidatesToFilter = (Set<Link>) filterDrivingDirectionCompatibleLinks(transferZone.getGeometry(), Collections.singleton(osmAccessMode), candidatesToFilter);
//
//      /* 2) make sure a valid stop_location on each remaining link can be created (for example if stop_location would be on an extreme node, it is possible no access link segment upstream of that node remains
//       *    which would render an otherwise valid position invalid */
//      Iterator<? extends Edge> iterator = candidatesToFilter.iterator();
//      while(iterator.hasNext()) {
//        Edge candidateLink = iterator.next();
//        Point connectoidLocation = getConnectoidHelper().findConnectoidLocationForstandAloneTransferZoneOnLink(
//            transferZone, (Link)candidateLink, accessMode, getSettings().getStopToWaitingAreaSearchRadiusMeters(), networkLayer);
//        if(connectoidLocation == null) {
//          iterator.remove();
//        }
//      }
//
//      if(candidatesToFilter == null || candidatesToFilter.isEmpty() ) {
//        logWarningIfNotNearBoundingBox(String.format("DISCARD: No suitable stop_location on potential osm way candidates found for transfer zone %s and mode %s", transferZone.getExternalId(), accessMode.getName()), transferZone.getGeometry());
//        return null;
//      }
//
//      /* 3) filter based on link hierarchy using osm way types, the premise being that bus services tend to be located on main roads, rather than smaller roads
//       * there is no hierarchy for rail, so we only do this for road modes. This could allow slightly misplaced waiting areas with multiple options near small and big roads
//       * to be salvaged in favour of the larger road */
//      if(OsmRoadModeTags.isRoadModeTag(osmAccessMode)){
//        OsmWayUtils.removeEdgesWithOsmHighwayTypesLessImportantThan(OsmWayUtils.findMostProminentOsmHighWayType(candidatesToFilter), candidatesToFilter);
//      }
//
//      if(candidatesToFilter.size()==1) {
//        selectedAccessLink = (Link) candidatesToFilter.iterator().next();
//      }else {
//        /* 4) still multiple options, now select closest from the remaining candidates */
//        selectedAccessLink = (Link)PlanitGraphGeoUtils.findEdgeClosest(transferZone.getGeometry(), candidatesToFilter, getGeoUtils());
//      }
//
//    }
//
//    if(salvaging == true) {
//      LOGGER.info(String.format("SALVAGED: Used non-closest osm way to %s %s to ensure waiting area %s is on correct side of road for mode %s",
//          selectedAccessLink.getExternalId(), selectedAccessLink.getName() != null ?  selectedAccessLink.getName() : "" , transferZone.getExternalId(), osmAccessMode));
//    }
//
//    return selectedAccessLink;

    return null;
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
  private void processNewStopPlatform(GtfsStop gtfsStop) {
  }

  /**
   * Process a GTFS stop that could be matched to nearby existing transfer zone(s). It will trigger the
   * augmenting the most suitable existing transfer zone with the GTFS stop information
   *
   * @param gtfsStop      to create new TransferZone for
   */
  private void matchToExistingTransferZone(final GtfsStop gtfsStop, final Collection<TransferZone> nearbyTransferZones) {
    PlanItRunTimeException.throwIfNull(gtfsStop,"GTFS stop null, this is not allowed");
    PlanItRunTimeException.throwIfNullOrEmpty(nearbyTransferZones,"No nearby transfer zones provided, this is not allowed");

    final Mode gtfsStopMode = data.getSupportedPtMode(gtfsStop);
    final Point projectedGtfsStopLocation = (Point) PlanitJtsUtils.transformGeometry(gtfsStop.getLocationAsPoint(), data.getCrsTransform());

    LoggingUtils.LogSevereIfNull(gtfsStopMode, LOGGER, "GTFSStop %s has no known PLANit mode that uses the stop", gtfsStop.getStopId());

    if(!data.getSettings().getAcivatedPlanitModes().contains(gtfsStopMode)){
      return;
    }

    /* remove nearby zones that are not mode compatible */
    nearbyTransferZones.removeIf(tz -> data.getSupportedPtModesIn(tz, Set.of(gtfsStopMode)).isEmpty());
    if(nearbyTransferZones.isEmpty()){
      processNewStopPlatform(gtfsStop);
      return;
    }

    /* remove transfer zones not on the correct side of the road, where rail is acceptable in both directions always */
    if((gtfsStopMode.hasPhysicalFeatures() && !(gtfsStopMode.getPhysicalFeatures().getTrackType() == TrackModeType.RAIL))){
      nearbyTransferZones.removeIf(tz -> !isGtfsStopOnCorrectSideOfTransferZoneAccessLinkSegments(gtfsStop, tz));
      if(nearbyTransferZones.isEmpty()) {
        /* non-rail match residing on wrong side of road, so we can't use it as a match. Instead, we'll have to create a new transfer zone */
        processNewStopPlatform(gtfsStop);
        return;
      }
    }


    /* find most appropriate link segment without considering transfer zones. If the found access point is much closer than any
    *  existing transfer zone, then we should not consider it a match even if it is possible to match it given location, mode, etc.
    *  if the found access link has a node that is the same as the access node of the best transfer zone match (see below) we can safely
    *  map it. If it is not play with warnings. If we do not find any closeby infrastructure ad no transferzone we also have a problem (network bounding box?)
    *  that might be worth logging
     */
    var searchBoundingBox = data.getGeoTools().createBoundingBox(projectedGtfsStopLocation.getEnvelopeInternal(), data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters());
    var nearbyLinks = GeoContainerUtils.<Link>queryEdgeQuadtree(searchBoundingBox, data.getGeoIndexedExistingLinks());

    //TODO: implement this
    var proposedAccessLink = findMostAppropriateAccessLinkSegmentForGtfsStop(gtfsStop, gtfsStopMode, nearbyLinks);
    //use result later on depending on where we end up with the matching on platform name... possibly restructure so we only have to
    // do the above if no match on platform name is found



    /* try to match on platform name first as it is most trustworthy... */
    var matchedTransferZone = matchByPlatform(gtfsStop, nearbyTransferZones);
    if(matchedTransferZone == null){
      /* ... else find closest existing transfer zone */
      matchedTransferZone = findTransferZoneStopLocationClosestTo(projectedGtfsStopLocation.getCoordinate(), nearbyTransferZones).first();

      /* when non rail mode, we would expect only a single mapping, log info when we find multiple to let user verify*/
      if (data.hasMappedGtfsStop(matchedTransferZone) && !(gtfsStopMode.getPhysicalFeatures().getTrackType() == TrackModeType.RAIL)){
        var earlierMappedStop = data.getMappedGtfsStop(getLastTransferZoneExternalId(matchedTransferZone));
        LOGGER.warning(String.format("PLANit transfer zone (%s) already mapped to GTFS stop (%s, %s, %s), adding another mapping for GTFS STOP (%s, %s, %s), consider verifying correctness",
            matchedTransferZone.getXmlId(), earlierMappedStop.getStopId(),  earlierMappedStop.getStopName(), earlierMappedStop.getLocationAsCoord().toString(), gtfsStop.getStopId(), gtfsStop.getStopName(), gtfsStop.getLocationAsCoord().toString()));
      }
    }else{
      data.getProfiler().incrementMatchedTransferZonesOnPlatformName();
    }

    /* augment external id with GTFS stop id + index by this id in separate map */
    if(!matchedTransferZone.getExternalId().contains(gtfsStop.getStopId())) {
      matchedTransferZone.appendExternalId(gtfsStop.getStopId());
    }

    /* supplement platform information if any is present on GTFS but not on transfer zone so far */
    if(gtfsStop.hasPlatformCode()){
      matchedTransferZone.addTransferZonePlatformName(gtfsStop.getPlatformCode());
    }

    /* update tracking data */
    data.registerMappedGtfsStop(gtfsStop, matchedTransferZone);
    LOGGER.info(String.format("Mapped GTFS stop %s at location %s to existing Transfer zone %s",gtfsStop.getStopId(), gtfsStop.getLocationAsCoord().toString(), matchedTransferZone.getXmlId()));

    //todo -> now either use the existing mapping to the physical network to update the reference on the service node of the stop
    //        or alternatively if there is no mapping yet create the mapping (isolate the OSM code that does this, make it generic and reuse it)

    /* profile */
    data.getProfiler().incrementAugmentedTransferZones();
  }


  /**
   * Process the GTFS stop which is marked as stop platform and fuse it with the PLANit memory model and previously parsed GTFS entities
   *
   * @param gtfsStop to processs
   */
  private void handleStopPlatform(GtfsStop gtfsStop) {
      data.getProfiler().incrementCount(GtfsObjectType.STOP);

      Collection<TransferZone> nearbyTransferZones = findNearbyTransferZones(gtfsStop.getLocationAsPoint(), data.getSettings().getGtfsStopToTransferZoneSearchRadiusMeters());

      if(nearbyTransferZones.isEmpty()){
        processNewStopPlatform(gtfsStop);
        return;
      }
      matchToExistingTransferZone(gtfsStop, nearbyTransferZones);
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

