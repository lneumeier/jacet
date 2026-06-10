package de.irotation.jacet.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for the {@code --changed} mode. These drive a real {@code git} executable against a throwaway
 * repository created per test, so they are skipped (not failed) when git is unavailable.
 */
class GitChangedTest {

  @TempDir
  Path tempDir;

  @BeforeAll
  static void requireGit() {
    Assumptions.assumeTrue(GitTestSupport.gitAvailable(), "git not on PATH; skipping --changed integration tests");
  }

  @Test
  void writeFormatsModifiedTrackedFile() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("Tracked.java");
    Files.writeString(file, "public class Tracked {}\n");
    this.commit("Tracked.java");
    Files.writeString(file, "public   class Tracked{void x(  ){return ;}}");

    final var result = GitTestSupport.runInRepo(tempDir, "--changed", "--write");

    assertEquals(0, result.exitCode());
    assertTrue(Files.readString(file).contains("public class Tracked"), "modified tracked file should be formatted");
  }

  @Test
  void writeFormatsUntrackedNewFile() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("New.java");
    Files.writeString(file, "public   class New{}");

    final var result = GitTestSupport.runInRepo(tempDir, "--changed", "--write");

    assertEquals(0, result.exitCode());
    assertTrue(Files.readString(file).contains("public class New"), "untracked new file should be formatted");
  }

  @Test
  void checkReturnsOneWhenChangedFileUnformatted() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("New.java");
    Files.writeString(file, "public   class New{}");

    final var result = GitTestSupport.runInRepo(tempDir, "--changed", "--check");

    assertEquals(1, result.exitCode());
    assertTrue(result.stdout().contains("New.java"));
  }

  @Test
  void stagedCleanFileIsNotTouched() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("Staged.java");
    final String unformatted = "public   class Staged{}";
    Files.writeString(file, unformatted);
    GitTestSupport.git(tempDir, "add", "Staged.java");

    final var result = GitTestSupport.runInRepo(tempDir, "--changed", "--write");

    assertEquals(0, result.exitCode());
    assertEquals(unformatted, Files.readString(file), "a staged file with no further worktree change is not 'changed'");
  }

  @Test
  void writeFormatsInPlaceWithoutStaging() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("Mod.java");
    Files.writeString(file, "public class Mod {}\n");
    this.commit("Mod.java");
    Files.writeString(file, "public   class Mod{void x(  ){return ;}}");

    final var result = GitTestSupport.runInRepo(tempDir, "--changed", "--write");

    assertEquals(0, result.exitCode());
    assertTrue(Files.readString(file).contains("public class Mod"), "working tree should be formatted");
    assertTrue(this.stagedNames().isEmpty(), "--changed must not stage anything");
  }

  @Test
  void emptyChangeSetExitsZeroQuietly() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("Clean.java");
    Files.writeString(file, "public class Clean {}\n");
    this.commit("Clean.java");

    final var result = GitTestSupport.runInRepo(tempDir, "--changed", "--write");

    assertEquals(0, result.exitCode());
  }

  @Test
  void notAGitRepositoryReturnsTwo() {
    final var result = GitTestSupport.runInRepo(tempDir, "--changed", "--write");

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("git repository"));
  }

  @Test
  void changedWithPositionalFilesIsRejected() throws Exception {
    GitTestSupport.initRepo(tempDir);

    final var result = GitTestSupport.runInRepo(tempDir, "--changed", tempDir.resolve("Any.java").toString());

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("cannot be combined with file arguments"));
  }

  @Test
  void stagedAndChangedTogetherIsRejected() {
    final var result = GitTestSupport.runInRepo(tempDir, "--staged", "--changed");

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("mutually exclusive"));
  }

  private void commit(final String relativePath) throws Exception {
    GitTestSupport.git(tempDir, "add", relativePath);
    GitTestSupport.git(tempDir, "commit", "-q", "-m", "init");
  }

  /** Repo-relative paths reported by {@code git diff --cached --name-only} (index vs. HEAD) in {@link #tempDir}. */
  private String stagedNames() throws Exception {
    return GitTestSupport.exec(tempDir, "git", "diff", "--cached", "--name-only").trim();
  }
}
