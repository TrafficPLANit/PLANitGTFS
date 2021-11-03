package org.goplanit.gtfs.util;

import org.goplanit.gtfs.enums.GtfsFileType;
import org.goplanit.gtfs.enums.GtfsFileTypePresence;

/**
 * Class indicating conditions regarding the presence of the GTFS file type
 * 
 * @author markr
 *
 */
public class GtfsFileConditions {
  
  /**
   * Type of presence condition
   */
  private final GtfsFileTypePresence presenceCondition;
  
  /**
   * other file absence that makes this condition to become required (can be null)
   */
  private final GtfsFileType requiredWhenAbsent;

  /**
   * other file presence that makes this condition to become required (can be null)
   */  
  private final GtfsFileType requiredWhenPresent;

  
  /** Constructor 
   * 
   * @param presenceCondition to use
   * @param requiredWhenAbsent to use
   * @param requiredWhenPresent to use
   */
  protected GtfsFileConditions(GtfsFileTypePresence presenceCondition, GtfsFileType requiredWhenAbsent, GtfsFileType requiredWhenPresent) {
    this.presenceCondition = presenceCondition;
    this.requiredWhenAbsent = requiredWhenAbsent;
    this.requiredWhenPresent = requiredWhenPresent;
  }
  
  /** Create a file condition indicating it is required
   * 
   * @return created file condition
   */
  public static GtfsFileConditions required() {
    return new GtfsFileConditions(GtfsFileTypePresence.REQUIRED, null, null);
  }
  
  /** Create a file condition indicating it is optional
   * 
   * @return created file condition
   */
  public static GtfsFileConditions optional() {
    return new GtfsFileConditions(GtfsFileTypePresence.OTPTIONAL, null, null);
  }
  
  /** Create a file condition indicating it is required if another file is present
   *
   * @param otherFileType when present this is also required to be present
   * @return created file condition
   */  
  public static GtfsFileConditions requiredinPresenceOf(GtfsFileType otherFileType) {
    return new GtfsFileConditions(GtfsFileTypePresence.CONDITIONALLY_REQUIRED, null, otherFileType);
  }
  
  /** Create a file condition indicating it is required if another file is not present
  *
  * @param otherFileType when not present this is required to be present
  * @return created file condition
  */    
  public static GtfsFileConditions requiredInAbsenceOf(GtfsFileType otherFileType) {
    return new GtfsFileConditions(GtfsFileTypePresence.CONDITIONALLY_REQUIRED, otherFileType, null);
  }
  
  /** Verify if required
   * 
   * @return true when required false otherwise
   */
  public boolean isRequired() {
    return presenceCondition.equals(GtfsFileTypePresence.REQUIRED);
  }

  /** Verify if conditionally required
   * 
   * @return true when conditionally required false otherwise
   */  
  public boolean isConditionallyRequired() {
    return presenceCondition.equals(GtfsFileTypePresence.CONDITIONALLY_REQUIRED);
  }
  
  /** Verify if optional
   * 
   * @return true when optional false otherwise
   */  
  public boolean isOptional() {
    return presenceCondition.equals(GtfsFileTypePresence.OTPTIONAL);
  }  

  /** Collect the dependency on other file type's presence/or not. Only relevant when conditionally required
   * 
   * @return other file type
   */
  public GtfsFileType getOtherFileDependency() {
    return requiredWhenAbsent==null ? requiredWhenPresent: requiredWhenAbsent;
  }
  
  /**
   * Check if conditional requirement depends on other file being present
   * 
   * @return true when dependent on other file presence and conditionally required (or required)
   */
  public boolean isRequiredWhenOtherFilePresent() {
    return isRequired() || (isConditionallyRequired() && (requiredWhenAbsent==null ? true: false));
  }      
  
  /**
   * Check if conditional requirement depends on other file being absent
   * 
   * @return true when dependent on other file absence and conditionally required (or required)
   */  
  public boolean isRequiredWhenOtherFileAbsent() {
    return isRequired() || (isConditionallyRequired() && (requiredWhenAbsent==null ? false: true));
  }    
}
