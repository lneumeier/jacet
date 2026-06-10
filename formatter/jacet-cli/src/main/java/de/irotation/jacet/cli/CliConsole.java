package de.irotation.jacet.cli;

import java.io.IOException;
import java.io.PrintStream;

/**
 * The CLI's output sink: every user-facing message goes through {@link #out} (results, formatted file lists) or {@link #err} (diagnostics,
 * warnings, errors), and the {@code --stdin-filepath} data channel through {@link #writeStdout}. Wrapping the two streams in one place keeps
 * the rest of the CLI free of direct {@code System.out}/{@code System.err} references, so the destination can be swapped (tests, a future log
 * framework) by handing {@link Main} a different instance.
 */
final class CliConsole {

  private final PrintStream out;
  private final PrintStream err;

  CliConsole(final PrintStream out, final PrintStream err) {
    this.out = out;
    this.err = err;
  }

  /**
   * A console bound to the current {@code System.out}/{@code System.err}. Resolved at call time, so a stream swap performed before this is
   * invoked (as the tests do) is honoured.
   */
  static CliConsole system() {
    return new CliConsole(System.out, System.err);
  }

  /** Prints a result line to standard output (formatted/unformatted file names). */
  void out(final String line) {
    this.out.println(line);
  }

  /** Prints a diagnostic line to standard error (warnings, errors, summaries). */
  void err(final String line) {
    this.err.println(line);
  }

  /** Writes raw bytes to standard output — the {@code --stdin-filepath} data channel, which must stay byte-exact. */
  void writeStdout(final byte[] bytes) throws IOException {
    this.out.write(bytes);
    this.out.flush();
  }
}
