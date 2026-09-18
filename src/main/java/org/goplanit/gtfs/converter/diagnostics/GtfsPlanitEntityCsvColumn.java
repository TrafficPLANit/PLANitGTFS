package org.goplanit.gtfs.converter.diagnostics;

import java.util.Arrays;

/**
 * Columns of the PLANit entity issue listing, i.e. one row per entity that could not be built. Declaration order is the
 * column order in the file, so the values written and the headers written cannot drift apart.
 *
 * @author markr
 */
public enum GtfsPlanitEntityCsvColumn {

  /** PLANit entity type the issue applies to */
  ENTITY_TYPE,

  /** the issue itself */
  ISSUE,

  /** what the issue says about the parser */
  DISPOSITION,

  /** GTFS entities the entity was to be built from */
  SOURCE_GTFS_ENTITIES,

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
    return Arrays.stream(values()).map(GtfsPlanitEntityCsvColumn::getHeader).toArray(String[]::new);
  }
}
