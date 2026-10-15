package org.goplanit.gtfs.converter.diagnostics;

import org.goplanit.utils.exceptions.PlanItRunTimeException;

import java.util.regex.Pattern;

/**
 * The entity specific context accompanying an issue, in the form a log line has room for and in the fuller form a
 * listing can afford, together with the arguments both are composed from.
 * <p>
 * The arguments an issue expects are derived from its templates rather than declared beside them, so the two cannot
 * drift apart, and a call site supplying the wrong number fails where it is wrong rather than quietly producing a
 * malformed detail.
 * </p>
 * <p>
 * Both forms are composed from the same arguments, so a form wanting only some of them selects those positionally,
 * e.g. {@code %2$s}. A template using positional references must use them throughout, since mixing the two forms makes
 * which argument lands where depend on their order in the template.
 * </p>
 *
 * @author markr
 */
class GtfsIssueDetail {

  /** an issue taking no context at all, shared since it holds nothing */
  static final GtfsIssueDetail NONE = new GtfsIssueDetail(null, null);

  /**
   * Matches a single format specifier, excluding an escaped percent sign, so the arguments a template expects can be
   * derived from the template itself
   */
  private static final Pattern FORMAT_SPECIFIER =
      Pattern.compile("%(?!%)(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]");

  /** format of the context as it is logged, null when the issue takes none */
  private final String loggedTemplate;

  /** format of the context as it is persisted, null when the logged form is used */
  private final String persistedTemplate;

  /** how many arguments a call site must supply, i.e. whatever the wider of the two forms requires */
  private final int argCount;

  /**
   * Constructor
   *
   * @param loggedTemplate format of the context as it is logged, may be null
   * @param persistedTemplate format of the context as it is persisted, may be null
   */
  private GtfsIssueDetail(final String loggedTemplate, final String persistedTemplate) {
    this.loggedTemplate = loggedTemplate;
    this.persistedTemplate = persistedTemplate;
    this.argCount = Math.max(countRequiredArgs(loggedTemplate), countRequiredArgs(persistedTemplate));
  }

  /**
   * Create context that reads the same wherever it appears
   *
   * @param loggedTemplate format of the context, null when the issue takes none
   * @return created context
   */
  static GtfsIssueDetail create(final String loggedTemplate) {
    return create(loggedTemplate, null);
  }

  /**
   * Create context that states more where there is room for it
   *
   * @param loggedTemplate format of the context as it is logged, null when the issue takes none
   * @param persistedTemplate format of the context as it is persisted, null to use the logged form
   * @return created context
   */
  static GtfsIssueDetail create(final String loggedTemplate, final String persistedTemplate) {
    if (loggedTemplate == null && persistedTemplate == null) {
      return NONE;
    }
    return new GtfsIssueDetail(loggedTemplate, persistedTemplate);
  }

  /**
   * Count the arguments a template requires. A template referring to its arguments positionally requires as many as its
   * highest reference, regardless of how many times it uses them; one referring to them in order requires one per
   * specifier
   *
   * @param template to count for, may be null
   * @return number of arguments required
   */
  private static int countRequiredArgs(final String template) {
    if (template == null) {
      return 0;
    }

    int sequentialArgs = 0;
    int highestPositionalArg = 0;
    var matcher = FORMAT_SPECIFIER.matcher(template);
    while (matcher.find()) {
      if (matcher.group(1) == null) {
        ++sequentialArgs;
      } else {
        highestPositionalArg = Math.max(highestPositionalArg, Integer.parseInt(matcher.group(1)));
      }
    }

    PlanItRunTimeException.throwIf(
        sequentialArgs > 0 && highestPositionalArg > 0,
        "GTFS issue detail template %s mixes positional and in order argument references", template);

    return Math.max(sequentialArgs, highestPositionalArg);
  }

  /**
   * Compose the context as it is logged
   *
   * @param issueName the context belongs to, reported when the arguments supplied are not the ones expected
   * @param detailArgs to compose from, none when the issue takes no context
   * @return composed context, null when the issue takes none
   */
  String createLogged(final String issueName, final Object... detailArgs) {
    return compose(issueName, loggedTemplate, detailArgs);
  }

  /**
   * Compose the context as it is persisted, falling back on the logged form where the issue declared only the one
   *
   * @param issueName the context belongs to, reported when the arguments supplied are not the ones expected
   * @param detailArgs to compose from, none when the issue takes no context
   * @return composed context, null when the issue takes none
   */
  String createPersisted(final String issueName, final Object... detailArgs) {
    return compose(issueName, persistedTemplate != null ? persistedTemplate : loggedTemplate, detailArgs);
  }

  /**
   * Compose one of the two forms, verifying that the arguments supplied are the ones the issue declared it needs
   *
   * @param issueName the context belongs to, reported when the arguments supplied are not the ones expected
   * @param template to compose, may be null
   * @param detailArgs to compose from
   * @return composed context, null when there is no template
   */
  private String compose(final String issueName, final String template, final Object... detailArgs) {
    int suppliedArgCount = detailArgs == null ? 0 : detailArgs.length;
    PlanItRunTimeException.throwIf(
        suppliedArgCount != argCount,
        "GTFS issue %s expects %d detail argument(s) but %d were supplied",
        issueName, argCount, suppliedArgCount);

    return template == null ? null : String.format(template, detailArgs);
  }

  /**
   * Collect the format of the context as it is logged
   *
   * @return logged template, null when the issue takes no context
   */
  String getLoggedTemplate() {
    return loggedTemplate;
  }

  /**
   * Collect the format of the context as it is persisted
   *
   * @return persisted template, null when the logged form is used
   */
  String getPersistedTemplate() {
    return persistedTemplate;
  }

  /**
   * Verify whether context accompanies the issue in the log
   *
   * @return true when it does, false otherwise
   */
  boolean hasLoggedTemplate() {
    return loggedTemplate != null;
  }

  /**
   * Verify whether context accompanies the issue when persisted, which it does whenever either form is present since
   * the persisted form falls back on the logged one
   *
   * @return true when it does, false otherwise
   */
  boolean hasPersistedTemplate() {
    return persistedTemplate != null || loggedTemplate != null;
  }

  /**
   * Collect how many arguments composing either form requires
   *
   * @return number of arguments
   */
  int getArgCount() {
    return argCount;
  }
}
