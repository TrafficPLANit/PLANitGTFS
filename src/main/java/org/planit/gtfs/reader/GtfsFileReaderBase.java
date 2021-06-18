package org.planit.gtfs.reader;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.gtfs.model.GtfsObject;
import org.planit.gtfs.scheme.GtfsFileScheme;

/**
 * A GTFS file reader containing generic code for any GTFS file
 * 
 * @author markr
 *
 */
public class GtfsFileReaderBase {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsFileReaderBase.class.getCanonicalName());
  
  /** file scheme containing the information regarding what GTFS file to parse and how */
  private final GtfsFileScheme fileScheme;
  
  /** registered handlers to use for each entry parsed */
  private final Set<GtfsFileHandler<? extends GtfsObject>> handlers;

  /** Constructor
   * 
   * @param fileScheme the file scheme this fiel reader is based on
   */
  public GtfsFileReaderBase(final GtfsFileScheme fileScheme) {
    this.fileScheme = fileScheme;
    this.handlers = new HashSet<GtfsFileHandler<? extends GtfsObject>>();
  }
  
  /** Register handler
   * 
   * @param handler to register
   */
  public void addHandler(final GtfsFileHandler<? extends GtfsObject> handler) {
    if(!handler.isCompatible(fileScheme)) {
      LOGGER.warning(String.format("DISCARD: GTFS handler incompatible with GTFS file reader for %s", fileScheme.toString()));
    }
    handlers.add(handler);
  }

  public GtfsFileScheme getFileScheme() {
    return fileScheme;
  }
}
