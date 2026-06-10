package de.irotation.jacet;

import java.util.List;

/**
 * Result of formatting a single source file.
 *
 * @param source             the formatted source, or the original input verbatim if {@code formatted} is {@code false}
 * @param formatted          {@code true} when the formatter produced output; {@code false} when parsing failed, or output
 *                           verification rejected the result, and the input was returned as-is
 * @param parseErrors        ANTLR syntax errors encountered during parsing, each as a {@code "line L:C message"} string
 * @param verificationErrors token-coverage violations found by {@code CoverageVerifier} (lost/duplicated code tokens or comments);
 *                           non-empty when a delegate formatter dropped or duplicated input — verification always runs
 */
public record FormatResult(String source, boolean formatted, List<String> parseErrors, List<String> verificationErrors) {

  /**
   * {@code true} if parsing the source produced any syntax errors. When true, {@link #source} is the unchanged input.
   */
  public boolean hasParseErrors() {
    return !parseErrors.isEmpty();
  }

  /**
   * {@code true} if output verification rejected the formatted result. When true, {@link #source} is the unchanged input
   * (the formatter fell back rather than emit output that lost or duplicated a token).
   */
  public boolean hasVerificationErrors() {
    return !verificationErrors.isEmpty();
  }
}
