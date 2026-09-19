package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.gtfs.enums.GtfsObjectType;
import org.goplanit.utils.misc.Triple;

/**
 * Where an entity stands in every respect a run is narrowed by, taken together.
 * <p>
 * Kept as one value rather than a scope per respect because the respects are only useful jointly: an entity that could
 * actually have been parsed is one within scope in every respect that applies to it, and that cannot be recovered from
 * the respects counted separately.
 * </p>
 * <p>
 * Every combination is created once up front and handed out thereafter, so an entity holds a reference and nothing
 * more, and a respect settling is a lookup rather than an allocation. There are only
 * {@code GtfsEntityScope.values().length ^ GtfsScopeDimension.values().length} of them
 * </p>
 *
 * @author markr
 */
public class GtfsScopeState {

  /** every combination, indexed by the scopes it holds */
  private static final GtfsScopeState[] INTERNED;

  static {
    var scopes = GtfsEntityScope.values();
    INTERNED = new GtfsScopeState[scopes.length * scopes.length * scopes.length];
    for (var spatial : scopes) {
      for (var temporal : scopes) {
        for (var modal : scopes) {
          INTERNED[indexOf(spatial, temporal, modal)] = new GtfsScopeState(spatial, temporal, modal);
        }
      }
    }
  }

  /** the scopes held, in the order the respects are declared */
  private final Triple<GtfsEntityScope, GtfsEntityScope, GtfsEntityScope> scopes;

  /**
   * Constructor
   *
   * @param spatial scope
   * @param temporal scope
   * @param modal scope
   */
  private GtfsScopeState(
      final GtfsEntityScope spatial, final GtfsEntityScope temporal, final GtfsEntityScope modal) {
    this.scopes = Triple.of(spatial, temporal, modal);
  }

  /**
   * Collect the position the given combination is held at
   *
   * @param spatial scope
   * @param temporal scope
   * @param modal scope
   * @return position
   */
  private static int indexOf(
      final GtfsEntityScope spatial, final GtfsEntityScope temporal, final GtfsEntityScope modal) {
    var range = GtfsEntityScope.values().length;
    return (spatial.ordinal() * range * range) + (temporal.ordinal() * range) + modal.ordinal();
  }

  /**
   * Collect the state holding the given scopes
   *
   * @param spatial scope
   * @param temporal scope
   * @param modal scope
   * @return state
   */
  public static GtfsScopeState of(
      final GtfsEntityScope spatial, final GtfsEntityScope temporal, final GtfsEntityScope modal) {
    return INTERNED[indexOf(spatial, temporal, modal)];
  }

  /**
   * Collect the state an entity of the given type starts in, i.e. unsettled in every respect that applies to it and
   * inapplicable in the rest
   *
   * @param entityType to collect for
   * @return state
   */
  public static GtfsScopeState unsettledFor(final GtfsObjectType entityType) {
    return of(
        scopeAtStart(GtfsScopeDimension.SPATIAL, entityType),
        scopeAtStart(GtfsScopeDimension.TEMPORAL, entityType),
        scopeAtStart(GtfsScopeDimension.MODAL, entityType));
  }

  /**
   * Collect the scope a respect starts in for the given entity type
   *
   * @param dimension respect concerned
   * @param entityType to collect for
   * @return scope
   */
  private static GtfsEntityScope scopeAtStart(
      final GtfsScopeDimension dimension, final GtfsObjectType entityType) {
    return dimension.appliesTo(entityType) ? GtfsEntityScope.NOT_ESTABLISHED : GtfsEntityScope.NOT_APPLICABLE;
  }

  /**
   * Collect the scope held for the given respect
   *
   * @param dimension to collect for
   * @return scope
   */
  public GtfsEntityScope get(final GtfsScopeDimension dimension) {
    switch (dimension) {
      case SPATIAL:
        return scopes.first();
      case TEMPORAL:
        return scopes.second();
      case MODAL:
        return scopes.third();
      default:
        throw new IllegalArgumentException(
            String.format("Unrecognised GTFS scope dimension %s encountered", dimension));
    }
  }

  /**
   * Collect the state this one becomes once the given respect settles at the given scope, the rest unchanged
   *
   * @param dimension that settled
   * @param scope it settled at
   * @return state
   */
  public GtfsScopeState with(final GtfsScopeDimension dimension, final GtfsEntityScope scope) {
    switch (dimension) {
      case SPATIAL:
        return of(scope, scopes.second(), scopes.third());
      case TEMPORAL:
        return of(scopes.first(), scope, scopes.third());
      case MODAL:
        return of(scopes.first(), scopes.second(), scope);
      default:
        throw new IllegalArgumentException(
            String.format("Unrecognised GTFS scope dimension %s encountered", dimension));
    }
  }

  /**
   * Verify whether the entity was within scope in every respect that applies to it, which is what makes it one the run
   * could actually have parsed. A respect that does not apply cannot rule anything out and so does not weigh in
   *
   * @return true when within scope throughout, false otherwise
   */
  public boolean isWithinScopeThroughout() {
    for (var dimension : GtfsScopeDimension.values()) {
      var scope = get(dimension);
      if (scope.isApplicable() && !scope.isWithinArea()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Verify whether any respect settled at all
   *
   * @return true when at least one settled, false otherwise
   */
  public boolean hasAnySettled() {
    for (var dimension : GtfsScopeDimension.values()) {
      if (get(dimension).isEstablished()) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return scopes.toString();
  }
}
