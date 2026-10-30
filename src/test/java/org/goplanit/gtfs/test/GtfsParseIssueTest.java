package org.goplanit.gtfs.test;

import org.goplanit.gtfs.converter.diagnostics.GtfsParseIssue;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify that every GTFS parse issue is reportable, i.e. that its wording is well formed and that the arguments it
 * declares can actually be applied to it.
 * <p>
 * The converter has carried several format strings whose placeholders did not match the arguments supplied, each of
 * which compiled and reached a run before being noticed. Now that the wording sits on the issue rather than at the call
 * site, every template can be exercised here instead
 * </p>
 *
 * @author markr
 */
public class GtfsParseIssueTest {

  /** matches a single format specifier, capturing its argument position where the template states one */
  private static final Pattern FORMAT_SPECIFIER =
      Pattern.compile("%(?!%)(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]");

  /**
   * Create the arguments an issue's template expects, as strings since every value a call site supplies is rendered
   * through {@code %s}
   *
   * @param issue to create for
   * @return created arguments
   */
  private static Object[] createDummyArgs(final GtfsParseIssue issue) {
    var args = new Object[issue.getDetailArgCount()];
    Arrays.setAll(args, index -> "arg" + index);
    return args;
  }

  /**
   * Every issue must compose its detail without error when given the number of arguments it declares
   */
  @Test
  public void everyIssueComposesItsDetailTest() {
    for (var issue : GtfsParseIssue.values()) {
      assertDoesNotThrow(() -> issue.createDetail(createDummyArgs(issue)), issue.name());
    }
  }

  /**
   * An issue without a template composes to nothing, one with a template composes to a populated string carrying every
   * argument supplied
   */
  @Test
  public void composedDetailReflectsTemplatePresenceTest() {
    for (var issue : GtfsParseIssue.values()) {
      var detail = issue.createDetail(createDummyArgs(issue));
      if (!issue.hasDetailTemplate()) {
        assertNull(detail, issue.name());
        /* an issue may still take arguments while logging none of them, since its persisted form states more */
        assertEquals(
            issue.hasPersistedDetailTemplate(), issue.getDetailArgCount() > 0, issue.name());
        continue;
      }

      assertNotNull(detail, issue.name());
      assertTrue(issue.getDetailArgCount() > 0, issue.name());
    }
  }

  /**
   * Every issue must compose its persisted detail without error and leave no unconsumed placeholder, the persisted form
   * falling back on the logged one where the issue declares only that
   */
  @Test
  public void everyIssueComposesItsPersistedDetailTest() {
    for (var issue : GtfsParseIssue.values()) {
      assertDoesNotThrow(() -> issue.createPersistedDetail(createDummyArgs(issue)), issue.name());

      var persistedDetail = issue.createPersistedDetail(createDummyArgs(issue));
      if (!issue.hasPersistedDetailTemplate()) {
        assertNull(persistedDetail, issue.name());
        continue;
      }
      assertNotNull(persistedDetail, issue.name());
      assertEquals(-1, persistedDetail.indexOf('%'), issue.name());
    }
  }

  /**
   * The persisted form is the record of an occurrence, so every argument a call site is made to supply must appear in
   * it. A logged form may show fewer, since it competes for room on a single line, but an argument collected and then
   * dropped from the record would be work done for nothing
   */
  @Test
  public void persistedDetailCarriesEveryArgumentTest() {
    for (var issue : GtfsParseIssue.values()) {
      if (!issue.hasPersistedDetailTemplate()) {
        continue;
      }
      var persistedDetail = issue.createPersistedDetail(createDummyArgs(issue));
      for (var arg : createDummyArgs(issue)) {
        assertTrue(persistedDetail.contains(arg.toString()), issue.name() + " omits " + arg);
      }
    }
  }

  /**
   * A template referring to its arguments positionally must do so throughout. Mixing the two forms is accepted by the
   * formatter but makes which argument lands where depend on the order the references appear in, so a template growing
   * a single in order reference would silently reshuffle what it reports
   */
  @Test
  public void noTemplateMixesArgumentReferenceStylesTest() {
    for (var issue : GtfsParseIssue.values()) {
      for (var template : new String[] {issue.getDetailTemplate(), issue.getPersistedDetailTemplate()}) {
        if (template == null) {
          continue;
        }
        var matcher = FORMAT_SPECIFIER.matcher(template);
        boolean positional = false;
        boolean inOrder = false;
        while (matcher.find()) {
          positional |= matcher.group(1) != null;
          inOrder |= matcher.group(1) == null;
        }
        assertTrue(!(positional && inOrder), issue.name() + " mixes argument reference styles in " + template);
      }
    }
  }

  /**
   * A template must hold no unconsumed placeholder, which composing with the declared arguments and checking for a
   * remaining percent sign establishes
   */
  @Test
  public void composedDetailLeavesNoPlaceholderTest() {
    for (var issue : GtfsParseIssue.values()) {
      if (!issue.hasDetailTemplate()) {
        continue;
      }
      assertEquals(-1, issue.createDetail(createDummyArgs(issue)).indexOf('%'), issue.name());
    }
  }

  /**
   * Supplying too few arguments must fail rather than compose a message holding a literal placeholder
   */
  @Test
  public void tooFewDetailArgumentsThrowsTest() {
    for (var issue : GtfsParseIssue.values()) {
      if (issue.getDetailArgCount() == 0) {
        continue;
      }
      var tooFew = new Object[issue.getDetailArgCount() - 1];
      Arrays.setAll(tooFew, index -> "arg" + index);
      assertThrows(PlanItRunTimeException.class, () -> issue.createDetail(tooFew), issue.name());
    }
  }

  /**
   * Supplying more arguments than the template consumes must fail rather than silently dropping them
   */
  @Test
  public void tooManyDetailArgumentsThrowsTest() {
    for (var issue : GtfsParseIssue.values()) {
      var tooMany = new Object[issue.getDetailArgCount() + 1];
      Arrays.setAll(tooMany, index -> "arg" + index);
      assertThrows(PlanItRunTimeException.class, () -> issue.createDetail(tooMany), issue.name());
    }
  }

  /**
   * Every issue must state a description, a disposition, a stage, an entity type and a log policy, since all five are
   * read when it is reported
   */
  @Test
  public void everyIssueIsFullyDescribedTest() {
    for (var issue : GtfsParseIssue.values()) {
      assertNotNull(issue.getStage(), issue.name());
      assertNotNull(issue.getEntityType(), issue.name());
      assertNotNull(issue.getDisposition(), issue.name());
      assertNotNull(issue.getLogPolicy(), issue.name());
      assertNotNull(issue.getDescription(), issue.name());
      assertTrue(!issue.getDescription().isBlank(), issue.name());
    }
  }
}
