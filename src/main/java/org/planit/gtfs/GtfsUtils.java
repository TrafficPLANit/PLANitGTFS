package org.planit.gtfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.EnumSet;
import java.util.logging.Logger;

import org.planit.gtfs.enums.GtfsKeyType;
import org.planit.gtfs.enums.GtfsObjectType;
import org.planit.gtfs.model.GtfsObjectFactory;
import org.planit.gtfs.scheme.GtfsFileScheme;
import org.planit.utils.misc.UrlUtils;
import org.planit.utils.zip.ZipUtils;

/**
 * general utilities specific to this GTFS API
 * 
 * @author markr
 *
 */
public class GtfsUtils {
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsUtils.class.getCanonicalName());

  /** A GTFS location URL is valid when it is either a directory or a zip file from which we can source the various GTFS files
   * 
   * @param gtfsLocation to verify
   * @return true when valid, false otherwise
   */
  public static boolean isValidGtfsLocation(final URL gtfsLocation) {
    return UrlUtils.isLocalDirectory(gtfsLocation) || UrlUtils.isLocalZipFile(gtfsLocation);
  }

  /** Based on passed in location and the file scheme create an nput stream to the appropriate file
   * 
   * @param gtfsLocation to use (dir or zip)
   * @param fileScheme to use to extract correct file name from
   * @return input stream to GTFS file
   */
  public static InputStream createInputStream(URL gtfsLocation, GtfsFileScheme fileScheme) {
    if(gtfsLocation==null || fileScheme==null || !isValidGtfsLocation(gtfsLocation)) {
      return null;
    }
    
    try {
      if(UrlUtils.isLocalDirectory(gtfsLocation)) {
        return new FileInputStream(new File(gtfsLocation.toURI())); 
      }else if(UrlUtils.isLocalZipFile(gtfsLocation)) {
        return ZipUtils.getZipEntryInputStream(gtfsLocation, fileScheme.getFileType().value());
      }
    }catch(Exception e) {
      LOGGER.warning(String.format("Unable to create input stream fro GTFS location %s and file %s", gtfsLocation.toString(), fileScheme.getFileType().value()));
    }
    
    return null;
  }

  /** Collect the supported keys via reflection where it is assumed the object type's class has a default constructor. If so the supported keys
   * are collected via a temporary instance that is created to access the abstract method providing the type specific supported keys
   * 
   * @param objectType
   * @return
   */
  public static EnumSet<GtfsKeyType> getSupportedKeys(GtfsObjectType objectType) {
      return GtfsObjectFactory.create(objectType).getSupportedKeys();
  }
}
