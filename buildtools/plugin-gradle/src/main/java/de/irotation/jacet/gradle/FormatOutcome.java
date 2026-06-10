package de.irotation.jacet.gradle;

import de.irotation.jacet.FormatResult;

/**
 * Classifies a {@link FormatResult} into the single action a jacet task should take for one file.
 *
 * <p>Centralizing the parse → verification → diff ordering keeps {@link FormatTask} and {@link CheckTask}
 * in lockstep with each other and with the CLI: a file that fails parsing <em>or</em> output verification is
 * skipped with a warning rather than silently rewritten (format) or silently passed (check). Verification
 * failures in particular must never be swallowed — the token-coverage guard exists precisely to surface a
 * formatter that would otherwise drop or duplicate code.
 */
enum FormatOutcome {
  /** The file is already formatted; nothing to do. */

  UNCHANGED,

  /** The file parsed and verified cleanly but differs from the formatted output. */
  NEEDS_FORMAT,

  /** The file could not be parsed; the formatter returned the input unchanged. */
  PARSE_ERROR,

  /** Output verification rejected the formatted result (token coverage violated); the input was returned unchanged. */
  VERIFICATION_ERROR;

  /**
   * Maps a format result against its original source to the action to take. Parse and verification failures take
   * precedence over the diff check because in both cases {@code result.source()} equals the original input, so a
   * naive equality test would misread them as {@link #UNCHANGED}.
   */
  static FormatOutcome classify(final String source, final FormatResult result) {
    if (result.hasParseErrors()) {
      return PARSE_ERROR;
    }
    if (result.hasVerificationErrors()) {
      return VERIFICATION_ERROR;
    }
    return source.equals(result.source()) ? UNCHANGED : NEEDS_FORMAT;
  }
}
