package org.goplanit.gtfs.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.EnumSet;
import java.util.logging.Logger;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsKeyType;
import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.gtfs.model.GtfsObjectFactory;
import org.goplanit.gtfs.scheme.GtfsFileScheme;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.zip.ZipUtils;

/**
 * general utilities specific to this GTFS API
 * 
 * @author markr
 *
 */
public class GtfsUtils {
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(GtfsUtils.class.getCanonicalName());

  /** process a situation where a GTFS file is not found , i.e., the logging of it, on a GTFS file based on filePResence condition provided
   * 
   * @param e file not found exception to process
   * @param filePresenceCondition to base logging on
   */
  private static void processGtfsFileNotFound(String fileLocation, GtfsFileConditions filePresenceCondition) {
    /* ok if optional, otherwise maybe not and log appropriate message */
    if(!filePresenceCondition.isOptional()) {
      if(filePresenceCondition.isConditionallyRequired()) {
        GtfsFileType otherFile = filePresenceCondition.getOtherFileDependency();
        if(filePresenceCondition.isRequiredWhenOtherFileAbsent()) {
          LOGGER.info(String.format("Conditionally required file %s absent, ok as long as %s is present", fileLocation, otherFile.value()));  
        }else {
          LOGGER.info(String.format("Conditionally required file %s absent, ok as long as %s is absent", fileLocation, otherFile.value()));
        }
      }else {
        LOGGER.warning(String.format("Required file %s absent", fileLocation));
      }
    }
  }

  /** A GTFS location URL is valid when it is either a directory or a zip file from which we can source the various GTFS files
   * 
   * @param gtfsLocation to verify
   * @return true when valid, false otherwise
   */
  public static boolean isValidGtfsLocation(final URL gtfsLocation) {
    return UrlUtils.isLocalDirectory(gtfsLocation) || UrlUtils.isLocalZipFile(gtfsLocation);
  }

  /** Based on passed in location and the file scheme create an input stream to the appropriate file, log warnings if not present but conditions
   * require otherwise.
   * 
   * @param gtfsLocation to use (dir or zip)
   * @param fileScheme to use to extract correct file name from
   * @param filePresenceCondition indicates if the file should be present or not. If 
   * @return input stream to GTFS file
   */
  public static InputStream createInputStream(URL gtfsLocation, GtfsFileScheme fileScheme, GtfsFileConditions filePresenceCondition) {
    if(gtfsLocation==null || fileScheme==null || !isValidGtfsLocation(gtfsLocation)) {
      return null;
    }
    
    try {
      if(UrlUtils.isLocalDirectory(gtfsLocation)) {
        URL gtfsFileUrl = UrlUtils.appendRelativePathToURL(gtfsLocation, fileScheme.getFileType().value());
        return createFileInputStream(new File(gtfsFileUrl.toURI()), filePresenceCondition);
      }else if(UrlUtils.isLocalZipFile(gtfsLocation)) {                
        return createZipEntryInputStream(gtfsLocation,  fileScheme.getFileType().value(), filePresenceCondition); 
      }
    } catch (URISyntaxException e) {
      LOGGER.warning(String.format("Invalid URL/file scheme provided (%s - %s) to create GTFS input stream for",gtfsLocation.toString(), fileScheme.getFileType().value()));
    }
    
    return null;
  }

  /** Create a zip based input stream for given zip internal file location and issue warnings dependent on the  presence conditions imposed if it is not present
   * 
   * @param gtfsLocation zip file to create input stream for
   * @param zipInternalFileName file internal to zip file to create stream for
   * @param filePresenceCondition for this file
   * @return created input stream, null if not available
   * @throws URISyntaxException when URL cannot be converted to URI to append internal file name
   */  
  public static InputStream createZipEntryInputStream(URL gtfsLocation, String zipInternalFileName, GtfsFileConditions filePresenceCondition) throws URISyntaxException {                
    InputStream zis = null;
    try {
      zis = ZipUtils.createZipEntryInputStream(gtfsLocation, zipInternalFileName);
    } catch (FileNotFoundException fnfe) {
      LOGGER.warning(String.format("Zip file %s not found",gtfsLocation.toString()));
      return null;
    } catch( IOException ioe) {
      LOGGER.warning(String.format("Unable to close Zip file %s after opening",gtfsLocation.toString()));
      return null;
    }
    
    /* zip file present, but internal file is not */
    if(zis==null) {
      processGtfsFileNotFound(gtfsLocation.toString(), filePresenceCondition);
    }
    return zis;        
  }

  /** Create a file based input stream for given file and issue warnings dependent on the  presence conditions imposed if it is not present
   * 
   * @param gtfsFileLocation to create input stream for
   * @param filePresenceCondition for this file
   * @return created input stream, null if not available
   */
  public static FileInputStream createFileInputStream(File gtfsFileLocation, GtfsFileConditions filePresenceCondition) {
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(gtfsFileLocation);
    } catch (FileNotFoundException e) {
      processGtfsFileNotFound(gtfsFileLocation.toString(), filePresenceCondition);
    }    
    return fis;
  }

  /** Collect the supported keys via reflection where it is assumed the object type's class has a default constructor. If so the supported keys
   * are collected via a temporary instance that is created to access the abstract method providing the type specific supported keys
   * 
   * @param objectType to use
   * @return supported keys
   */
  public static EnumSet<GtfsKeyType> getSupportedKeys(GtfsObjectType objectType) {
      return GtfsObjectFactory.create(objectType).getSupportedKeys();
  }
}
