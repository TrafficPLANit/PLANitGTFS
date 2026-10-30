package org.goplanit.gtfs.converter.diagnostics;

import java.util.Arrays;

/**
 * Columns of the coverage summary holding the totals the logged summary is derived from. Declaration order is the
 * column order in the file, so the values written and the headers written cannot drift apart.
 * <p>
 * Each row is a disjoint bucket within an entity type, being a category it was registered under such as a route type,
 * or an empty category holding whatever was registered without one. Summing the rows of a type therefore yields that
 * type's totals, which is why no row restating those totals is written.
 * </p>
 *
 * @author markr
 */
public enum GtfsCoverageCsvColumn {

  /** GTFS entity type the row reports on */
  ENTITY_TYPE,

  /** subtype within the entity type, empty where the type is not subdivided */
  SUBTYPE,

  /** where the entities sit relative to the area the run covers */
  SPATIAL_SCOPE,

  /** whether the entities run on the day and within the time period the run was configured for */
  TEMPORAL_SCOPE,

  /** whether the entities serve a mode activated for the run */
  MODAL_SCOPE,

  /** whether the entities were ones the run was asked to include at all */
  SELECTION_SCOPE,

  /** how many entities the feed holds of this type, subtype and scope */
  COUNT;

  /**
   * Collect the column as it appears in the header row
   *
   * @return column name in lower case
   */
  public String getHeader() {
    return name().toLowerCase();
  }

  /**
   * Collect every column header in column order
   *
   * @return headers
   */
  public static String[] getHeaders() {
    return Arrays.stream(values()).map(GtfsCoverageCsvColumn::getHeader).toArray(String[]::new);
  }
}
