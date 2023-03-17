package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.service.GtfsServicesHandlerProfiler;
import org.goplanit.gtfs.converter.service.GtfsServicesReaderSettings;
import org.goplanit.gtfs.converter.service.handler.GtfsServicesHandlerData;
import org.goplanit.gtfs.enums.RouteType;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.MacroscopicNetworkLayerConfigurator;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test services reader settings
 */
public class ServicesReaderSettingsAndDataTest {

  GtfsServicesReaderSettings settings;

  GtfsServicesHandlerData data;

  MacroscopicNetwork parentNetwork;
  ServiceNetwork serviceNetwork;
  RoutedServices routedServices;

  @Before
  public void before(){

    /* parent network with modes train and bus on single layer */
    parentNetwork = new MacroscopicNetwork(IdGenerator.createIdGroupingToken("testToken"));
    parentNetwork.getModes().getFactory().registerNew(PredefinedModeType.BUS);
    parentNetwork.getModes().getFactory().registerNew(PredefinedModeType.TRAIN);
    parentNetwork.initialiseLayers(MacroscopicNetworkLayerConfigurator.createAllInOneConfiguration(parentNetwork.getModes()));
    settings  = new GtfsServicesReaderSettings("InputSource", "country", RouteTypeChoice.ORIGINAL);

    serviceNetwork = new ServiceNetwork(parentNetwork.getIdGroupingToken(), parentNetwork);
    routedServices = new RoutedServices(parentNetwork.getIdGroupingToken(), serviceNetwork);
  }

  @Test
  public void settingsTest1() {
    data = new GtfsServicesHandlerData(settings, serviceNetwork, routedServices, new GtfsServicesHandlerProfiler());

    /* mapping for already present modes */
    assertThat(data.getPlanitModeIfActivated(RouteType.BUS), is(parentNetwork.getModes().get((PredefinedModeType.BUS))));
    assertThat(data.getPlanitModeIfActivated(RouteType.TROLLEY_BUS), is(parentNetwork.getModes().get((PredefinedModeType.BUS))));
    assertThat(data.getPlanitModeIfActivated(RouteType.RAIL), is(parentNetwork.getModes().get((PredefinedModeType.TRAIN))));

    /* other mappings should have been added as well */
    assertThat(data.getPlanitModeIfActivated(RouteType.SUBWAY_METRO), is(parentNetwork.getModes().get((PredefinedModeType.SUBWAY))));

    /* remove all mappings except one */
    settings.deactivateAllModesExcept(List.of(RouteType.BUS,RouteType.TROLLEY_BUS));
    data = new GtfsServicesHandlerData(settings, serviceNetwork, routedServices, new GtfsServicesHandlerProfiler());

    assertThat(data.getPlanitModeIfActivated(RouteType.BUS), is(parentNetwork.getModes().get((PredefinedModeType.BUS))));
    assertThat(data.getPlanitModeIfActivated(RouteType.TROLLEY_BUS), is(parentNetwork.getModes().get((PredefinedModeType.BUS))));
    assert(data.getPlanitModeIfActivated(RouteType.RAIL)==null);
    assert(data.getPlanitModeIfActivated(RouteType.SUBWAY_METRO)==null);

  }

  @Test
  public void timePeriodSettingsTest(){

    /* time period filters */
    assertThat(settings.hasTimePeriodFilters(), is(false));

    settings.setDayOfWeek(DayOfWeek.TUESDAY);
    assertThat(settings.getDayOfWeek(), is(DayOfWeek.TUESDAY));

    settings.addTimePeriodFilter(LocalTime.of(6,0,0), LocalTime.of(9,0,0));

    assertThat(settings.hasTimePeriodFilters(), is(true));

    var pmPeriod = Pair.of(LocalTime.of(17,0,0), LocalTime.of(19,0,0));
    settings.addTimePeriodFilter(pmPeriod.first(), pmPeriod.second());

    assertThat(settings.getTimePeriodFilters().size(), is(2));

    /* add to same filter, since time period is the same, it should be ignored and warning should be logged*/
    settings.addTimePeriodFilter(pmPeriod.first(), pmPeriod.second());
    assertThat(settings.getTimePeriodFilters().size(), is(2));

  }
}
