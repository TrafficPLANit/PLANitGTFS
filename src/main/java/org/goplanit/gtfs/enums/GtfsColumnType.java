package org.goplanit.gtfs.enums;

/**
 * Initial filter for setting up which columns are to be included in the creation of the settings for a GTFS file reader
 * <ul>
 *   <li>ALL_COLUMNS - includes all columns</li>
 *   <li>PLANIT_REQUIRED_COLUMNS - includes the minimum amount of columns to convert GTFS to PLANit memory model</li>
 *   <li>NO_COLUMNS - excludes all columns and requires user to explicitly enable columns afterwards</li>
 * </ul>
 */
public enum GtfsColumnType {
  ALL_COLUMNS,
  PLANIT_REQUIRED_COLUMNS,
  NO_COLUMNS
}
