package org.goplanit.gtfs.converter.diagnostics;

import java.util.Arrays;

/**
 * Columns of the per entity issue listings, i.e. one row per recorded occurrence. Declaration order is the column
 * order in the file, so the values written and the headers written cannot drift apart.
 *
 * @author markr
 */
public enum GtfsIssueCsvColumn {

  /** stage the issue arose in */
  STAGE,

  /** GTFS entity type the issue applies to */
  ENTITY_TYPE,

  /** the issue itself */
  ISSUE,

  /** what the issue says about the parser */
  DISPOSITION,

  /** what became of the entity */
  OUTCOME,

  /** GTFS id of the entity concerned */
  ENTITY_ID,

  /** further context supplied by the call site */
  DETAIL;

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
    return Arrays.stream(values()).map(GtfsIssueCsvColumn::getHeader).toArray(String[]::new);
  }
}
