package org.goplanit.gtfs.test.utils;

import org.goplanit.converter.idmapping.IdMapperType;
import org.goplanit.gtfs.converter.zoning.GtfsZoningReaderSettings;

/**
 * Dedicated class to minimise warnings that have been verified as ignorable and switch them off accordingly
 */
public class MelbourneGtfsZoningSettingsUtils {


  /**
   * When applied to the GTFs resources it suppresses and addresses warnings deemed issues that are NOT to be fixed in the parser
   * but would detract from assessing the logs.
   *
   * @param settings to apply to
   * @param preExistingTransferZonesPresent when true there already exist transfer zones to map to, when false not
   */
  public static void minimiseVerifiedWarnings2023(GtfsZoningReaderSettings settings , boolean preExistingTransferZonesPresent) {

    /* GTFS STOPS WITH NEARBY OSM STOPS WITH ISSUES TO CORRECT */
    if(preExistingTransferZonesPresent) {

      /* GTFS is clearly wrong, logging indicates non-closest road selected and verified this is the wrong road, move to
       * appropriate location */
      settings.addOverwriteGtfsStopTransferZoneMapping("16830","2021079560",IdMapperType.EXTERNAL_ID);

      /* correctly inferred but far away so warning is issued, override to suppress warning */
      settings.addOverwriteGtfsStopTransferZoneMapping("18095","413591731",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("18174","390550952",IdMapperType.EXTERNAL_ID);

      /* correctly inferred from multiple transfer zone options, but since they use the same access link, info message is issued,
       * suppress this with explicit mapping */
      settings.addOverwriteGtfsStopTransferZoneMapping("19725","217509580",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("23173","7244246423",IdMapperType.EXTERNAL_ID);

      /* incorrectly inferred from multiple options, override default behaviour with correct OSM stop */
      settings.addOverwriteGtfsStopTransferZoneMapping("40645","7246376796",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("41373","7246376796",IdMapperType.EXTERNAL_ID);

      /* just on wrong side of road, but OSM pole nearby, use that */
      settings.addOverwriteGtfsStopTransferZoneMapping("19522","3945796018",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("19548","8495987158",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("19549","8495987158",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("2111","6775928326",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("43718","8495987158",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("50835","3937111754",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("50837","3937113666",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("51143","3937113666",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("51808","8488622237",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("5918","6136859935",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("7460","8488802789",IdMapperType.EXTERNAL_ID);
      settings.addOverwriteGtfsStopTransferZoneMapping("87","3924765036",IdMapperType.EXTERNAL_ID);


      /* GTFS stop represents multiple transfer zones (stacked on top of each other at Melbourne central/Parliament/Richmond). Map the stops to
       * all (multi-level) platforms --> in absence of OSM this will go wrong because it will only choose one rail track */
      {
        settings.addOverwriteGtfsStopTransferZoneMapping("19842","459973944",IdMapperType.EXTERNAL_ID);//central
        settings.addOverwriteGtfsStopTransferZoneMapping("19842","459973945",IdMapperType.EXTERNAL_ID);
        settings.addOverwriteGtfsStopTransferZoneMapping("19843","458461655",IdMapperType.EXTERNAL_ID);//parliament
        settings.addOverwriteGtfsStopTransferZoneMapping("19843","458461657",IdMapperType.EXTERNAL_ID);

        /* richmond exists multiple times for different types of trains (train/vline), so apply all platforms to be sure */
        settings.addOverwriteGtfsStopTransferZoneMapping("19908","45403078",IdMapperType.EXTERNAL_ID); //richmond 9&10
        settings.addOverwriteGtfsStopTransferZoneMapping("19908","45403082",IdMapperType.EXTERNAL_ID); //richmond 7&8
        settings.addOverwriteGtfsStopTransferZoneMapping("19908","45403085",IdMapperType.EXTERNAL_ID); //richmond 5&6
        settings.addOverwriteGtfsStopTransferZoneMapping("19908","45368228",IdMapperType.EXTERNAL_ID); //richmond 3&4
        settings.addOverwriteGtfsStopTransferZoneMapping("22247","45368128",IdMapperType.EXTERNAL_ID); //richmond 1&2
        settings.addOverwriteGtfsStopTransferZoneMapping("22247","45403078",IdMapperType.EXTERNAL_ID); //richmond 9&10
        settings.addOverwriteGtfsStopTransferZoneMapping("22247","45403082",IdMapperType.EXTERNAL_ID); //richmond 7&8
        settings.addOverwriteGtfsStopTransferZoneMapping("22247","45403085",IdMapperType.EXTERNAL_ID); //richmond 5&6
        settings.addOverwriteGtfsStopTransferZoneMapping("22247","45368228",IdMapperType.EXTERNAL_ID); //richmond 3&4
        settings.addOverwriteGtfsStopTransferZoneMapping("22247","45368128",IdMapperType.EXTERNAL_ID); //richmond 1&2

        settings.addOverwriteGtfsStopTransferZoneMapping("19976","45479014",IdMapperType.EXTERNAL_ID); //collingwood
        settings.addOverwriteGtfsStopTransferZoneMapping("19976","45479019",IdMapperType.EXTERNAL_ID); //collingwood

      }

    }else{

      /* in case we did not parse OSM zones, we need to move the location of the above GTFS stops because they are in
       * the wrong location tagging error */
      settings.setOverwriteGtfsStopLocation("16830",-37.7986,144.95796);
      settings.setOverwriteGtfsStopLocation("19522",-37.7986,144.95796);
      settings.setOverwriteGtfsStopLocation("19548",-37.81417,144.95528);
      settings.setOverwriteGtfsStopLocation("19549",-37.81418,144.95528);
      settings.setOverwriteGtfsStopLocation("2111",-37.79730,144.96797);
      settings.setOverwriteGtfsStopLocation("43718",-37.81418, 144.95530);
      settings.setOverwriteGtfsStopLocation("50835",-37.80744,144.97130);
      settings.setOverwriteGtfsStopLocation("50837",-37.80810,144.97254);
      settings.setOverwriteGtfsStopLocation("51143",-37.81191,144.95912);
      settings.setOverwriteGtfsStopLocation("51808",-37.80963,144.95843);
      settings.setOverwriteGtfsStopLocation("5918",-37.81014,144.93704);
      settings.setOverwriteGtfsStopLocation("7460",-37.79989,144.95004);
      settings.setOverwriteGtfsStopLocation("87",-37.80008,144.96391);

    }

    /* GTFS STOPS WITHOUT NEARBY OSM STOPS WITH ISSUES TO CORRECT */

    /* no road connection nearby but valid GTFS stop,e.g. very large bus terminal for example, adjust location to avoid discarding the stop  */
    settings.setOverwriteGtfsStopLocation("23173",-37.81577, 144.95238); //southern cross bus terminal

    /* on wrong side of road, and/or too close to another road and/or no OSM stop to match to, causing the wrong mapping
     * overwrite location to more appropriate point to avoid this mismatch */
    settings.setOverwriteGtfsStopLocation("1122",-37.83108,144.98808);
    settings.setOverwriteGtfsStopLocation("16837",-37.81897,144.96178);
    settings.setOverwriteGtfsStopLocation("44828",-37.80209,144.95907);

    /* same reason as above, but here, alternatively, we map explicitly to the OSM way id representing the closest road
     *(communicated via the logging when it was identified to be on the wrong side of the road) and accept it remains
     * on the wrong side for example purposes */
    settings.overwriteGtfsStopToLinkMapping("1128","977507781", IdMapperType.EXTERNAL_ID);

    /* incorrect OSM way inferred, should choose the closest road, so override */
    settings.overwriteGtfsStopToLinkMapping("3226","9136109", IdMapperType.EXTERNAL_ID);
    settings.overwriteGtfsStopToLinkMapping("4891","25427777", IdMapperType.EXTERNAL_ID);

    /* correctly inferred from nearby links, despite closest link not being chosen, suppress warning via explicit mapping */
    settings.overwriteGtfsStopToLinkMapping("6530","984811437", IdMapperType.EXTERNAL_ID);
    settings.overwriteGtfsStopToLinkMapping("7575","9124321", IdMapperType.EXTERNAL_ID);

  }
}
