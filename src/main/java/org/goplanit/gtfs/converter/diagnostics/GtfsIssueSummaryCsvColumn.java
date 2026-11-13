package org.goplanit.gtfs.converter.diagnostics;

import java.util.Arrays;

/**
 * Columns of the per issue summary, i.e. one row per issue and subtype it arose within. Declaration order is the
 * column order in the file, so the values written and the headers written cannot drift apart.
 * <p>
 * The written counterpart of the logged summary. The log collapses an issue to a line so it stays readable; here the
 * same figures are carried in full, including what each share was measured against, which a log line can only name
 * rather than show.
 * </p>
 *
 * @author markr
 */
public enum GtfsIssueSummaryCsvColumn {

  /** stage the issue arose in */
  STAGE,

  /** GTFS entity type the issue applies to */
  ENTITY_TYPE,

  /** subtype of the entity type the row reports on, empty where the type is not subdivided */
  SUBTYPE,

  /** where the entities stood relative to the area the run covers when the issue arose */
  SPATIAL_SCOPE,

  /** how the entities stood to the day and time period the run covers when the issue arose */
  TEMPORAL_SCOPE,

  /** how the entities stood to the modes activated for the run when the issue arose */
  MODAL_SCOPE,

  /** whether the entities were ones the run was asked to include at all */
  SELECTION_SCOPE,

  /** the issue itself */
  ISSUE,

  /** what the issue says about the parser */
  DISPOSITION,

  /** what became of the entities it arose for */
  OUTCOME,

  /** how often the issue was registered */
  OCCURRENCES;


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
    return Arrays.stream(values()).map(GtfsIssueSummaryCsvColumn::getHeader).toArray(String[]::new);
  }
}
