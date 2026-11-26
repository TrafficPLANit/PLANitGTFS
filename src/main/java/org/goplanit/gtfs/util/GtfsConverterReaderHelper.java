package org.goplanit.gtfs.util;

import org.geotools.geometry.jts.JTS;
import org.goplanit.converter.utils.ProjectedBoundingAreaHelper;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettingsImpl;
import org.goplanit.gtfs.converter.GtfsConverterReaderSettingsWithModeMapping;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.locationtech.jts.geom.Polygon;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.mode.ModesImpl;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.mode.*;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Convenience methods usable across various GTFS converter readers
 *
 */
public class GtfsConverterReaderHelper {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(GtfsConverterReaderHelper.class.getCanonicalName());

  /**
   * Create the bounding area to restrict parsing to, based on the user defined polygon or, when none was set, on the
   * underlying network's "rough" bounding area. Either way GTFS entities falling outside it are discarded, the derived
   * area is only rougher than one drawn by the user.
   * <p>
   * Shared by the stages so what is in scope is the same question in each: a stop that one stage considers beyond the
   * area and another does not is worse than either answer on its own
   * </p>
   *
   * @param settings to source the user defined bounding area and ferry leniency from
   * @param referenceNetwork the GTFS entities are mapped onto, providing the fallback area and the destination CRS
   * @return created bounding area helper
   */
  public static ProjectedBoundingAreaHelper createBoundingAreaHelper(
      final GtfsConverterReaderSettingsImpl settings, final MacroscopicNetwork referenceNetwork) {

    var networkCrs = referenceNetwork.getCoordinateReferenceSystem();
    Polygon boundingPolygonInGtfsCrs = null;
    if(!settings.hasBoundingBoundary()){
      /* a network without any extent yet offers nothing to fall back on, leaving no area known and so nothing that
       * can be ruled beyond the area */
      var networkBoundingBox = referenceNetwork.createBoundingBox();
      if(networkBoundingBox != null) {
        var boundingPolygonInPlanitCrs = PlanitJtsUtils.create2DPolygon(networkBoundingBox);
        try{
          var crsTransformGtfsToPlanit = PlanitJtsUtils.findMathTransform(
              PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS, networkCrs);
          boundingPolygonInGtfsCrs = (Polygon) JTS.transform(
              boundingPolygonInPlanitCrs, crsTransformGtfsToPlanit.inverse());
        }catch (Exception e){}
      }
    }else{
      boundingPolygonInGtfsCrs = settings.getBoundingArea();
    }

    if(boundingPolygonInGtfsCrs == null){
      /* no area is known, neither drawn by the user nor derivable from the network, so nothing is beyond the area */
      return ProjectedBoundingAreaHelper.empty();
    }

    // use helper for quick indexed checks
    return ProjectedBoundingAreaHelper.of(
        boundingPolygonInGtfsCrs,
        PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS,
        networkCrs,
        settings.getMaximumDistanceFerryOutsideBoundingPolygonInMeters());
  }


  /**
   * Construct the instance mappings from GTFS mode to PLANit mode, based on the settings, which define both custom and
   * predefined mappings. A configured predefined mode type results in an instance of that type on a network, which
   * should be available in the passed in allAvailablePlanitModes. These mappings are combined with the custom mode
   * mappings on the settings into a single consolidated set, so parsing need not check whether a mode is predefined.
   *
   * @param settings the settings to obtain the activated two types od mode mappings from
   * @param allAvailablePlanitModes the mode instances used that should be a superset of the activated mode mappings on
   *                                the reader
   * @return consolidated set of both custom and predefined modes sourced from the allAvailablePlanitModes
   */
  public static HashMap<RouteType, List<Mode>> createCombinedActivatedPlanitModes(
      GtfsConverterReaderSettingsWithModeMapping settings, Modes allAvailablePlanitModes){
    var predefinedModeTypes = settings.getAcivatedPlanitPredefinedModes();

    /* first add predefined modes when present */
    Set<Mode> planitModeInstances = predefinedModeTypes.stream().map( pmt -> allAvailablePlanitModes.get(pmt)).filter(e -> e !=null).collect(Collectors.toSet());

    /* construct mapping, note that a gtfs mode can be mapped to multiple planit modes as well as multiple gtfs modes being mapped to the same planit mode */
    HashMap<RouteType, List<Mode>> gtfsModeToPlanitModeMapping = new HashMap<>();
    var activatedGtfsModes = settings.getAcivatedGtfsModes();
    for(var gtfsMode : activatedGtfsModes) {
      var mappedPredefinedPlanitModes = settings.getAcivatedPlanitPredefinedModes(gtfsMode);
      var availablePlanitModeInstances = planitModeInstances.stream().filter(
          m -> m.isPredefinedModeType() && mappedPredefinedPlanitModes.contains(m.getPredefinedModeType())).collect(Collectors.toList());
      gtfsModeToPlanitModeMapping.put(gtfsMode, availablePlanitModeInstances);
    }

    return gtfsModeToPlanitModeMapping;
  }

  /** add GTFS type Id to PLANit mode external id (in case multiple GTFS modes are mapped to the same PLANit mode)
   *
   * @param planitMode to update external id for
   * @param gtfsMode to use
   */
  public static void addToModeExternalId(Mode planitMode, RouteType gtfsMode){
    if(planitMode != null) {
      String gtfsModeId = String.valueOf(gtfsMode.getValue());
      if(planitMode.hasExternalId() && !planitMode.containsExternalId(';', gtfsModeId)) {
        planitMode.appendExternalId(gtfsModeId, ';');
      }else {
        planitMode.setExternalId(gtfsModeId);
      }
    }
  }

  /**
   * Make sure the mapped predefined PLANit modes exist as actual mode instances, adding them to the network's modes
   * where needed based on the configuration.
   * <p>
   *   A mode is only added if the network has at least one mode of the same track type (e.g., water, rail, road). If
   *   not, a warning is logged and the mode is deactivated on the GTFS settings. For example, with tram activated but
   *   no rail based mode layers in the network, there is no point in activating it
   * </p>
   *
   * @param settings to base modes configuration on
   * @param network to adjust modes for
   */
  public static void syncActivatedPlanitPredefinedModesBeforeParsing(GtfsServicesReaderSettings settings, MacroscopicNetwork network) {
    /* create temporary mode instances of each mode type */
    var token = IdGroupingToken.create("temp");
    var gtfsActivatedModes = new ModesImpl(token);
    settings.getAcivatedPlanitPredefinedModes().stream().forEach(mt -> gtfsActivatedModes.getFactory().registerNew(mt));

    /* find track types that are supported */
    var supportedTrackTypes = network.getModes().stream().filter( m -> m.hasPhysicalFeatures()).map(m -> m.getPhysicalFeatures().getTrackType()).collect(Collectors.toSet());

    for(var gtfsActivatedMode : gtfsActivatedModes){
      if(supportedTrackTypes.contains(gtfsActivatedMode.getPhysicalFeatures().getTrackType())) {
        network.getModes().getFactory().registerNew(gtfsActivatedMode.getPredefinedModeType());
      }else{
        LOGGER.warning(String.format(
            "Deactivating GTFS mode %s because its track type %s is not present on any mode [%s] in the physical network",
            gtfsActivatedMode.getPredefinedModeType(), gtfsActivatedMode.getPhysicalFeatures().getTrackType(), network.getModes().stream().map(m -> m.getName()).collect(Collectors.joining(","))));
        var gtfsRouteTypesToDeactivate = settings.getAcivatedGtfsModes(gtfsActivatedMode.getPredefinedModeType());
        settings.deactivateGtfsModes(gtfsRouteTypesToDeactivate);
      }
    }

    IdGenerator.reset(token);
  }
}
