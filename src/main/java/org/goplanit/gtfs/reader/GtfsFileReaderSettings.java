package org.goplanit.gtfs.reader;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.goplanit.gtfs.enums.GtfsKeyType;

/**
 * General settings applicable to all GTFS file readers
 * 
 * @author markr
 *
 */
public class GtfsFileReaderSettings {

  /** track explicitly excluded columns from parsing */
  private final Set<GtfsKeyType> excludedColumns = new HashSet<GtfsKeyType>();
  
  
  /** Exclude one or more columns from in memory object to for example reduce the memory foot print
   * 
   * @param columnsToExclude the columns to actively exclude
   */
  public void excludeColumns(GtfsKeyType... columnsToExclude) {
    Arrays.stream(columnsToExclude).forEach( key -> excludedColumns.add(key));
  }
  
  /** the excluded columns (unmodifiable)
   * 
   * @return excluded columns
   */
  public Set<GtfsKeyType> getExcludedColumns() {
    return Collections.unmodifiableSet(excludedColumns);
  }

  /** Verify if a column is excluded
   * 
   * @param column to check based on the GTFS key type it corresponds to
   * @return true when excluded, false otherwise
   */
  public boolean isExcludedColumn(GtfsKeyType column) {
    return excludedColumns.contains(column);
  }
  
}
