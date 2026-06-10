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
 * Integration tests for the {@code --staged} mode. These drive a real {@code git} executable against a throwaway
 * repository created per test, so they are skipped (not failed) when git is unavailable.
 */
class GitStagedTest {

  @TempDir
  Path tempDir;

  @BeforeAll
  static void requireGit() {
    Assumptions.assumeTrue(GitTestSupport.gitAvailable(), "git not on PATH; skipping --staged integration tests");
  }

  @Test
  void writeFormatsAndReStagesStagedFile() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("Bad.java");
    Files.writeString(file, "public   class Bad{public void foo(  ){return ;}}");
    GitTestSupport.git(tempDir, "add", "Bad.java");

    final var result = GitTestSupport.runInRepo(tempDir, "--staged", "--write");

    assertEquals(0, result.exitCode());
    assertTrue(Files.readString(file).contains("public class Bad"), "working tree should be formatted");
    assertTrue(this.unstagedNames().isEmpty(), "formatted file should be fully re-staged (no remaining worktree diff)");
  }

  @Test
  void checkReturnsOneWhenStagedFileUnformatted() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("Bad.java");
    Files.writeString(file, "public   class Bad{}");
    GitTestSupport.git(tempDir, "add", "Bad.java");

    final var result = GitTestSupport.runInRepo(tempDir, "--staged", "--check");

    assertEquals(1, result.exitCode());
    assertTrue(result.stdout().contains("Bad.java"));
  }

  @Test
  void checkReturnsZeroWhenStagedFileFormatted() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("Ok.java");
    Files.writeString(file, "public   class Ok{}");
    GitTestSupport.git(tempDir, "add", "Ok.java");
    GitTestSupport.runInRepo(tempDir, "--staged", "--write");

    final var result = GitTestSupport.runInRepo(tempDir, "--staged", "--check");

    assertEquals(0, result.exitCode());
  }

  @Test
  void partiallyStagedFileIsSkippedAndLeftUntouched() throws Exception {
    GitTestSupport.initRepo(tempDir);
    final Path file = tempDir.resolve("Partial.java");
    Files.writeString(file, "public   class Partial{}");
    GitTestSupport.git(tempDir, "add", "Partial.java");
    final String unstagedEdit = "public   class Partial{}\nclass Extra{}\n";
    Files.writeString(file, unstagedEdit);

    final var result = GitTestSupport.runInRepo(tempDir, "--staged", "--write");

    assertEquals(0, result.exitCode());
    assertTrue(result.stderr().contains("partially-staged"));
    assertEquals(unstagedEdit, Files.readString(file), "partially-staged file must not be reformatted");
  }

  @Test
  void emptyStagedSetExitsZeroQuietly() throws Exception {
    GitTestSupport.initRepo(tempDir);

    final var result = GitTestSupport.runInRepo(tempDir, "--staged", "--write");

    assertEquals(0, result.exitCode());
  }

  @Test
  void nonJavaStagedFileIsIgnored() throws Exception {
    GitTestSupport.initRepo(tempDir);
    Files.writeString(tempDir.resolve("notes.txt"), "not java");
    GitTestSupport.git(tempDir, "add", "notes.txt");

    final var result = GitTestSupport.runInRepo(tempDir, "--staged", "--write");

    assertEquals(0, result.exitCode());
  }

  @Test
  void stagedFileInSubdirectoryIsResolvedAndFormatted() throws Exception {
    GitTestSupport.initRepo(tempDir);
    Files.createDirectories(tempDir.resolve("src/main"));
    final Path file = tempDir.resolve("src/main/Deep.java");
    Files.writeString(file, "public   class Deep{}");
    GitTestSupport.git(tempDir, "add", "src/main/Deep.java");

    final var result = GitTestSupport.runInRepo(tempDir, "--staged", "--write");

    assertEquals(0, result.exitCode());
    assertTrue(Files.readString(file).contains("public class Deep"));
  }

  @Test
  void notAGitRepositoryReturnsTwo() {
    final var result = GitTestSupport.runInRepo(tempDir, "--staged", "--write");

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("git repository"));
  }

  @Test
  void stagedWithPositionalFilesIsRejected() throws Exception {
    GitTestSupport.initRepo(tempDir);

    final var result = GitTestSupport.runInRepo(tempDir, "--staged", tempDir.resolve("Any.java").toString());

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("cannot be combined with file arguments"));
  }

  /** Repo-relative paths reported by {@code git diff --name-only} (working tree vs. index) in {@link #tempDir}. */
  private String unstagedNames() throws Exception {
    return GitTestSupport.exec(tempDir, "git", "diff", "--name-only").trim();
  }
}
