public class SwitchCases {

  // switch-expression: arrow switch expression and classic colon switch
  public String toText(int num) {
    return switch (num) {
      case 1 -> "one";
      case 2 -> "two";
      case 3, 4, 5 -> "several";
      default -> {
        var result = "number:" + num;
        yield result;
      }
    };
  }

  public void classicSwitch(int x) {
    switch (x) {
      case 1:
        doOne();
        break;
      case 2:
        doTwo();
        break;
      default:
        doDefault();
        break;
    }
  }

  // switch-empty-default: classic switch with empty default
  public void dispatch(final Status status) {
    switch (status) {
      case READY:
        handleReady();
        break;
      case DONE:
        handleDone();
        break;
      default:
    }
  }

  private void handleReady() {}

  private void handleDone() {}

  // switch-arrow-body-comments: comments before arrow expression bodies
  String format(int type) {
    return switch (type) {
      case 1 ->
        // comment before expression
        computeValue();
      case 2 -> "simple";
      default ->
        // default comment
        fallback();
    };
  }

  // switch-arrow-case-list-wraps: long case label list wraps
  int test(String value) {
    return switch (value) {
      case "ALPHA_ONE", "BRAVO_TWO", "CHARLIE_THREE", "DELTA_FOUR", "ECHO_FIVE", "FOXTROT_SIX", "GOLF_SEVEN", "HOTEL_EIGHT", "INDIA_NINE", "JULIET_TEN" -> 1;
      case "NOVEMBER" -> 2;
      default -> 0;
    };
  }

  int testShort(String value) {
    return switch (value) {
      case "A", "B", "C" -> 1;
      default -> 0;
    };
  }

  // switch-arrow-double-comment: two trailing line comments after last case
  String describeDoubleComment(final Action action) {
    return switch (action) {
      case READ -> "RESULT-READ";

      // Note A: first hint
      // Note B: second hint
    };
  }

  // switch-trailing-comment-with-blank-line: trailing comment separated by blank line
  String describeTrailingWithBlank(final Action action) {
    return switch (action) {
      case READ -> "RESULT-READ";
      case SAVE -> "RESULT-SAVE";

      // Note: if more are added here, this must also be adjusted in the template
    };
  }

  // switch-trailing-comment-without-blank-line: trailing comment without blank line
  String describeTrailingWithoutBlank(final Action action) {
    return switch (action) {
      case READ -> "RESULT-READ";
      case SAVE -> "RESULT-SAVE";
      // Note: nothing more is added here
    };
  }

  // switch-pattern-guard-parens: pattern guard parens break onto own lines
  // A pattern guard `when (...)` is a root context: the parens break onto their own lines.
  long guardParenIsRoot(Object pair) {
    return switch (pair) {
      case Pair(final String value1, final String value2) when (
        StringUtils.isNumeric(value1) && StringUtils.isNumeric(value2) && bothValuesAreWithinTheExpectedNumericRange(value1, value2)
      ) -> Long.parseLong(value2) - Long.parseLong(value1);
      default -> 0L;
    };
  }

  // assignment-switch-expression-rhs: switch expression as assignment RHS
  void m() {
    final Object result = switch (someReasonablyLongFieldName) {
      case FIRST_VALUE -> ValueFormatter.formatDate(entity.getFirstValue());
      case SECOND_VALUE -> ValueFormatter.formatNumber(entity.getSecondValue());
      default -> null;
    };
  }
}
