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

  /** category within the entity type, empty on the row holding that type's totals */
  CATEGORY,

  /** entities of this type encountered in the feed */
  SEEN,

  /** entities accepted and represented in the memory model */
  PARSED,

  /** entities dropped because the run asked for it */
  DISCARDED_BY_DESIGN,

  /** entities dropped because the parser cannot handle them */
  DISCARDED_LIMITATION,

  /** entities that were meant to be usable and were not */
  DISCARDED_PROBLEM,

  /** issues recorded against entities that were parsed regardless */
  ISSUES_ON_PARSED;

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
