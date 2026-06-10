package de.irotation.jacet.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import de.irotation.jacet.FormatResult;
import de.irotation.jacet.JacetFormatter;

/**
 * Runs the formatter over a list of files in either {@code --check} or {@code --write} mode. Shared by every CLI mode (plain paths,
 * {@code --staged}, {@code --changed}); files that cannot be read, fail to parse, or fail output verification are reported and skipped
 * without changing the exit code.
 */
final class FormatRunner {

  private final JacetFormatter formatter;
  private final CliConsole console;

  FormatRunner(final JacetFormatter formatter, final CliConsole console) {
    this.formatter = formatter;
    this.console = console;
  }

  /** Checks each file; lists the unformatted ones on stdout and a summary on stderr. Returns 1 if any file is unformatted, else 0. */
  int check(final Iterable<Path> javaFiles) {
    final List<Path> unformatted = new ArrayList<>();
    int readErrors = 0;
    int parseErrors = 0;
    int verifyErrors = 0;

    for (final Path file : javaFiles) {
      final String source = this.readSource(file);
      if (source == null) {
        readErrors++;
        continue;
      }
      final FormatResult result = this.formatter.formatWithResult(source);

      if (result.hasParseErrors()) {
        parseErrors++;
        for (final String error : result.parseErrors()) {
          this.console.err("WARN: " + file + ": " + error);
        }
      } else if (result.hasVerificationErrors()) {
        verifyErrors++;
        this.reportVerificationFailure(file, result);
      } else if (!source.equals(result.source())) {
        unformatted.add(file);
      }
    }

    for (final Path file : unformatted) {
      this.console.out(file.toString());
    }
    if (!unformatted.isEmpty()) {
      this.console.err(unformatted.size() + " file(s) not formatted.");
    }
    if (parseErrors > 0) {
      this.console.err(parseErrors + " file(s) skipped due to parse errors.");
    }
    if (verifyErrors > 0) {
      this.console.err(verifyErrors + " file(s) skipped — verification failed.");
    }
    if (readErrors > 0) {
      this.console.err(readErrors + " file(s) skipped — unreadable.");
    }

    return unformatted.isEmpty() ? 0 : 1;
  }

  /**
   * Formats and rewrites each changed file, printing the written paths (stdout) and a summary (stderr). Returns the files actually written
   * plus the read/parse/verification skip counts, so callers can act on what changed (e.g. re-stage in git).
   */
  WriteOutcome write(final Iterable<Path> javaFiles) throws IOException {
    final List<Path> changed = new ArrayList<>();
    int readErrors = 0;
    int parseErrors = 0;
    int verifyErrors = 0;

    for (final Path file : javaFiles) {
      final String source = this.readSource(file);
      if (source == null) {
        readErrors++;
        continue;
      }
      final FormatResult result = this.formatter.formatWithResult(source);

      if (result.hasParseErrors()) {
        parseErrors++;
        for (final String error : result.parseErrors()) {
          this.console.err("WARN: " + file + ": " + error);
        }
      } else if (result.hasVerificationErrors()) {
        verifyErrors++;
        this.reportVerificationFailure(file, result);
      } else if (!source.equals(result.source())) {
        Files.writeString(file, result.source());
        this.console.out(file.toString());
        changed.add(file);
      }
    }

    final WriteOutcome outcome = new WriteOutcome(changed, readErrors, parseErrors, verifyErrors);
    this.reportWriteSummary(outcome);
    return outcome;
  }

  private void reportWriteSummary(final WriteOutcome outcome) {
    if (!outcome.changed().isEmpty()) {
      this.console.err(outcome.changed().size() + " file(s) formatted.");
    }
    if (outcome.verifyErrors() > 0) {
      this.console.err(outcome.verifyErrors() + " file(s) skipped — verification failed.");
    }
    if (outcome.parseErrors() > 0) {
      this.console.err(outcome.parseErrors() + " file(s) skipped due to parse errors.");
    }
    if (outcome.readErrors() > 0) {
      this.console.err(outcome.readErrors() + " file(s) skipped — unreadable.");
    }
  }

  /**
   * Reads one source file, or returns {@code null} after a warning when the file cannot be read or decoded (e.g. non-UTF-8 content) — a
   * single unreadable file must not abort the whole batch, mirroring the warn-and-skip handling of parse and verification failures.
   */
  private @Nullable String readSource(final Path file) {
    try {
      return Files.readString(file);
    } catch (final IOException e) {
      this.console.err("WARN: " + file + ": skipped — cannot read: " + e);
      return null;
    }
  }

  private void reportVerificationFailure(final Path file, final FormatResult result) {
    this.console.err("WARN: " + file + ": skipped — verification failed:");
    for (final String error : result.verificationErrors()) {
      this.console.err("  " + error);
    }
  }

  record WriteOutcome(List<Path> changed, int readErrors, int parseErrors, int verifyErrors) {}
}
