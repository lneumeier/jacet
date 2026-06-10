package de.irotation.jacet.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

/**
 * Thin wrapper around the {@code git} executable (assumed on {@code PATH}), used by the {@code --staged} and {@code --changed} modes to
 * discover the Java files to format (and, for {@code --staged}, re-stage them). All git interaction lives here so the rest of the CLI — and
 * {@code jacet-core} — stays git-agnostic.
 *
 * <p>Each method runs one {@code git} subprocess in {@link #baseDir}. Output of the listing commands is requested
 * NUL-delimited ({@code -z}) so paths with spaces or special characters survive parsing. Paths are repo-root-relative with forward slashes,
 * even on Windows.
 */
final class GitClient {

  private static final String NUL = String.valueOf((char) 0);

  /** Upper bound on a single git invocation, so a hung git (e.g. a credential prompt) can never wedge a pre-commit hook. */
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private final Path baseDir;

  GitClient(final Path baseDir) {
    this.baseDir = baseDir;
  }

  /**
   * Returns whether {@link #baseDir} lies inside a git work tree. A non-zero exit ("not a git repository") maps to {@code false}; a missing
   * {@code git} executable surfaces as {@link GitException}.
   */
  boolean isInsideWorkTree() {
    final GitResult result = this.run(List.of("rev-parse", "--is-inside-work-tree"), null);
    return result.exitCode() == 0 && "true".equals(result.stdout().trim());
  }

  /** Absolute path of the repository root ({@code git rev-parse --show-toplevel}). */
  Path repoRoot() {
    final GitResult result = this.run(List.of("rev-parse", "--show-toplevel"), null);
    if (result.exitCode() != 0) {
      throw new GitException("git rev-parse --show-toplevel failed: " + result.stderr().trim());
    }
    return Path.of(result.stdout().trim());
  }

  /** Repo-relative paths of staged (index) Java files: added, copied, modified or renamed. */
  List<String> stagedJavaFiles() {
    return this.listPaths(List.of("diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z", "--", "*.java"));
  }

  /** Repo-relative paths of Java files with unstaged working-tree changes (index vs. working tree). */
  List<String> unstagedJavaFiles() {
    return this.listPaths(List.of("diff", "--name-only", "-z", "--", "*.java"));
  }

  /** Repo-relative paths of untracked, non-ignored Java files (new files not yet added to git). */
  List<String> untrackedJavaFiles() {
    return this.listPaths(List.of("ls-files", "--others", "--exclude-standard", "-z", "--", "*.java"));
  }

  /**
   * Re-stages the given paths via {@code git add}. Paths are fed NUL-delimited through stdin
   * ({@code --pathspec-from-file=- --pathspec-file-nul}) so the call is immune to command-line length limits. Absolute paths are accepted;
   * git normalizes them against the work tree.
   */
  void add(final Collection<String> paths) {
    if (paths.isEmpty()) {
      return;
    }
    final byte[] stdin = String.join(NUL, paths).getBytes(StandardCharsets.UTF_8);
    final GitResult result = this.run(List.of("add", "--pathspec-from-file=-", "--pathspec-file-nul"), stdin);
    if (result.exitCode() != 0) {
      throw new GitException("git add failed: " + result.stderr().trim());
    }
  }

  private List<String> listPaths(final Collection<String> args) {
    final GitResult result = this.run(args, null);
    if (result.exitCode() != 0) {
      throw new GitException("git " + String.join(" ", args) + " failed: " + result.stderr().trim());
    }
    return splitNul(result.stdout());
  }

  /**
   * Runs one {@code git} subprocess and collects its output. stdout and stderr are drained on separate threads so a process that fills one
   * pipe while we read the other cannot deadlock, and the whole call is bounded by {@link #TIMEOUT} — a hung git is killed rather than left
   * to block indefinitely.
   */
  private GitResult run(final Collection<String> args, final byte @Nullable [] stdin) {
    final List<String> command = new ArrayList<>();
    command.add("git");
    command.addAll(args);

    final ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(this.baseDir.toFile());

    final Process process;
    try {
      process = builder.start();
    } catch (final IOException e) {
      throw new GitException("git executable not found on PATH (required for --staged/--changed)", e);
    }

    try (final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      final Future<byte[]> out = executor.submit(() -> process.getInputStream().readAllBytes());
      final Future<byte[]> err = executor.submit(() -> process.getErrorStream().readAllBytes());

      try (final OutputStream os = process.getOutputStream()) {
        if (stdin != null) {
          os.write(stdin);
        }
      }

      if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new GitException("git " + String.join(" ", args) + " timed out after " + TIMEOUT.toSeconds() + "s");
      }
      return new GitResult(
        process.exitValue(),
        new String(out.get(), StandardCharsets.UTF_8),
        new String(err.get(), StandardCharsets.UTF_8)
      );
    } catch (final IOException e) {
      process.destroyForcibly();
      throw new GitException("failed writing to git stdin", e);
    } catch (final ExecutionException e) {
      process.destroyForcibly();
      throw new GitException("failed reading git output", e.getCause() != null ? e.getCause() : e);
    } catch (final InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new GitException("interrupted while running git", e);
    }
  }

  private static List<String> splitNul(final String raw) {
    if (raw.isEmpty()) {
      return List.of();
    }
    final List<String> result = new ArrayList<>();
    for (final String token : raw.split(NUL)) {
      if (!token.isEmpty()) {
        result.add(token);
      }
    }
    return result;
  }

  private record GitResult(int exitCode, String stdout, String stderr) {}

  /** Unchecked failure of a git operation: missing executable, non-zero exit, or interruption. */
  static final class GitException extends RuntimeException {

    GitException(final String message) {
      super(message);
    }

    GitException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
