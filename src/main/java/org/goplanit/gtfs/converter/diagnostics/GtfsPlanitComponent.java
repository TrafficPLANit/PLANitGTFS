package org.goplanit.gtfs.converter.diagnostics;

/**
 * The PLANit component a derived entity ends up in, so that what became of each kind of entity can be read against the
 * part of the result it belongs to.
 *
 * @author markr
 */
public enum GtfsPlanitComponent {

  /** the zones stops are boarded from and the access points granting them entry to the physical network */
  ZONING("zoning"),

  /** the stops and legs the services run over */
  SERVICE_NETWORK("service network"),

  /** the services themselves, their trips and the times those depart */
  ROUTED_SERVICES("routed services");

  /** readable label used when reporting */
  private final String label;

  /**
   * Constructor
   *
   * @param label readable label used when reporting
   */
  GtfsPlanitComponent(final String label) {
    this.label = label;
  }

  /**
   * Collect the component as it reads in a log line
   *
   * @return label
   */
  public String getLabel() {
    return label;
  }
}
