package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.MacroscopicNetworkLayerConfigurator;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.mode.PredefinedModeType;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test services reader settings
 */
public class ServicesReaderSettingsTest {

  GtfsServicesReaderSettings settings;

  MacroscopicNetwork parentNetwork;

  @Before
  public void before(){

    /* parent network with modes train and bus on single layer */
    parentNetwork = new MacroscopicNetwork(IdGenerator.createIdGroupingToken("testToken"));
    parentNetwork.getModes().getFactory().registerNew(PredefinedModeType.BUS);
    parentNetwork.getModes().getFactory().registerNew(PredefinedModeType.TRAIN);
    parentNetwork.initialiseLayers(MacroscopicNetworkLayerConfigurator.createAllInOneConfiguration(parentNetwork.getModes()));
    settings  = new GtfsServicesReaderSettings("InputSource", "country", parentNetwork, RouteTypeChoice.ORIGINAL);
  }

  @Test
  public void settingsTest(){

    /* mapping for already present modes */
    assertThat(settings.getPlanitModeIfActivated(RouteType.BUS), is(parentNetwork.getModes().get((PredefinedModeType.BUS))));
    assertThat(settings.getPlanitModeIfActivated(RouteType.TROLLEY_BUS), is(parentNetwork.getModes().get((PredefinedModeType.BUS))));
    assertThat(settings.getPlanitModeIfActivated(RouteType.RAIL), is(parentNetwork.getModes().get((PredefinedModeType.TRAIN))));

    /* other mappings should have been added as well */
    assertThat(settings.getPlanitModeIfActivated(RouteType.SUBWAY_METRO), is(parentNetwork.getModes().get((PredefinedModeType.SUBWAY))));

    /* remove all mappings except one */
    settings.deactivateAllModesExcept(List.of(RouteType.BUS,RouteType.TROLLEY_BUS));

    assertThat(settings.getPlanitModeIfActivated(RouteType.BUS), is(parentNetwork.getModes().get((PredefinedModeType.BUS))));
    assertThat(settings.getPlanitModeIfActivated(RouteType.TROLLEY_BUS), is(parentNetwork.getModes().get((PredefinedModeType.BUS))));
    assert(settings.getPlanitModeIfActivated(RouteType.RAIL)==null);
    assert(settings.getPlanitModeIfActivated(RouteType.SUBWAY_METRO)==null);

    /* should reflect both bus and trolleybus numbers based on ';' separator */
    var externalId = parentNetwork.getModes().get(PredefinedModeType.BUS).getExternalId();
    assertThat(externalId, CoreMatchers.containsString(";"));
    assertThat(externalId, CoreMatchers.containsString(String.valueOf(RouteType.BUS.getValue())));
    assertThat(externalId, CoreMatchers.containsString(String.valueOf(RouteType.TROLLEY_BUS.getValue())));

    /* remove one of the bus mappings, external id should reflect this now */
    settings.deactivateGtfsMode(RouteType.TROLLEY_BUS);
    externalId = parentNetwork.getModes().get(PredefinedModeType.BUS).getExternalId();
    assertThat(externalId, not(CoreMatchers.containsString(";")));
    assertThat(externalId, CoreMatchers.containsString(String.valueOf(RouteType.BUS.getValue())));
    assertThat(externalId, not(CoreMatchers.containsString(String.valueOf(RouteType.TROLLEY_BUS.getValue()))));
  }
}