package de.irotation.jacet.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import picocli.CommandLine;

/** Shared helpers for the git-mode CLI integration tests ({@code --staged}, {@code --changed}). */
final class GitTestSupport {

  record CliResult(int exitCode, String stdout, String stderr) {}

  private GitTestSupport() {}

  /** Whether a {@code git} executable is on the PATH; tests use this to skip rather than fail when git is absent. */
  static boolean gitAvailable() {
    try {
      return new ProcessBuilder("git", "--version").start().waitFor() == 0;
    } catch (final IOException | InterruptedException e) {
      return false;
    }
  }

  /** Creates a fresh repository in {@code dir} with a committer identity and deterministic settings. */
  static void initRepo(final Path dir) throws Exception {
    git(dir, "init", "-q");
    git(dir, "config", "user.email", "test@example.com");
    git(dir, "config", "user.name", "Test");
    git(dir, "config", "commit.gpgsign", "false");
    git(dir, "config", "core.autocrlf", "false");
  }

  static void git(final Path dir, final String... args) throws Exception {
    final String[] command = new String[args.length + 1];
    command[0] = "git";
    System.arraycopy(args, 0, command, 1, args.length);
    exec(dir, command);
  }

  static String exec(final Path dir, final String... command) throws Exception {
    final Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
    final byte[] output = process.getInputStream().readAllBytes();
    final int exitCode = process.waitFor();
    final String text = new String(output, StandardCharsets.UTF_8);
    if (exitCode != 0) {
      throw new IllegalStateException("command failed (" + exitCode + "): " + String.join(" ", command) + "\n" + text);
    }
    return text;
  }

  /** Runs the jacet CLI in-process with git resolved against {@code repo}, capturing stdout, stderr and the exit code. */
  static CliResult runInRepo(final Path repo, final String... args) {
    final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    final PrintStream oldOut = System.out;
    final PrintStream oldErr = System.err;

    try {
      System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      final Main main = new Main();
      main.workingDir = repo;
      final int exitCode = new CommandLine(main).execute(args);
      return new CliResult(exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }
}
