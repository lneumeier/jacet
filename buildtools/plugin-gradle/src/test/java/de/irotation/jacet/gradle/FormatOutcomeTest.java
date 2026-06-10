package de.irotation.jacet.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.irotation.jacet.FormatResult;

class FormatOutcomeTest {

  private static final String SOURCE = "public class Foo {}\n";

  @Test
  void unchangedWhenOutputEqualsSourceAndNoErrors() {
    final FormatResult result = new FormatResult(SOURCE, true, List.of(), List.of());

    assertEquals(FormatOutcome.UNCHANGED, FormatOutcome.classify(SOURCE, result));
  }

  @Test
  void needsFormatWhenOutputDiffersAndNoErrors() {
    final FormatResult result = new FormatResult("public class Foo {\n}\n", true, List.of(), List.of());

    assertEquals(FormatOutcome.NEEDS_FORMAT, FormatOutcome.classify(SOURCE, result));
  }

  @Test
  void parseErrorIsReportedEvenThoughSourceIsUnchanged() {
    // On parse failure the formatter returns the input verbatim, so result.source() equals the original.
    final FormatResult result = new FormatResult(SOURCE, false, List.of("line 1:0 mismatched input"), List.of());

    assertEquals(FormatOutcome.PARSE_ERROR, FormatOutcome.classify(SOURCE, result));
  }

  @Test
  void verificationErrorIsNeverSilentlyTreatedAsUnchanged() {
    // The P0 regression: on a verification failure the formatter also returns the input verbatim, so a naive
    // source-equality check reads it as UNCHANGED and the skipped file is never surfaced. classify() must not.
    final FormatResult result = new FormatResult(SOURCE, false, List.of(), List.of("lost token 'class' at 1:7"));

    assertEquals(FormatOutcome.VERIFICATION_ERROR, FormatOutcome.classify(SOURCE, result));
  }

  @Test
  void parseErrorTakesPrecedenceOverVerificationError() {
    final FormatResult result = new FormatResult(SOURCE, false, List.of("parse boom"), List.of("verify boom"));

    assertEquals(FormatOutcome.PARSE_ERROR, FormatOutcome.classify(SOURCE, result));
  }
}
