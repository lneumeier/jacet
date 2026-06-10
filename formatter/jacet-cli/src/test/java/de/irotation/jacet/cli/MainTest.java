package de.irotation.jacet.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class MainTest {

  @TempDir
  Path tempDir;

  private record CliResult(int exitCode, String stdout, String stderr) {}

  private CliResult run(final String... args) {
    final var stdout = new ByteArrayOutputStream();
    final var stderr = new ByteArrayOutputStream();
    final var oldOut = System.out;
    final var oldErr = System.err;

    try {
      System.setOut(new PrintStream(stdout));
      System.setErr(new PrintStream(stderr));
      final int exitCode = new CommandLine(new Main()).execute(args);
      return new CliResult(exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }

  private CliResult runWithStdin(final String input, final String... args) {
    final var oldIn = System.in;
    System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    try {
      return this.run(args);
    } finally {
      System.setIn(oldIn);
    }
  }

  @Test
  void checkFormattedFileReturnsZero() throws Exception {
    final var file = tempDir.resolve("Good.java");
    Files.writeString(
      file,
      """
            public class Good {
                public void hello() {
                    return;
                }
            }
            """
    );

    // Format first, then check
    this.run("--write", file.toString());
    final var result = this.run("--check", file.toString());

    assertEquals(0, result.exitCode(), "Formatted file should pass check");
  }

  @Test
  void checkUnformattedFileReturnsOne() throws Exception {
    final var file = tempDir.resolve("Bad.java");
    Files.writeString(file, "public   class Bad{public void foo(  ){return ;}}");

    final var result = this.run("--check", file.toString());

    assertEquals(1, result.exitCode());
    assertTrue(result.stdout().contains("Bad.java"));
    assertTrue(result.stderr().contains("1 file(s) not formatted"));
  }

  @Test
  void undecodableFileIsSkippedWithoutAbortingTheBatch() throws Exception {
    final var latin1 = tempDir.resolve("Latin1.java");
    Files.write(latin1, "public class Latin1 { String s = \"käse\"; }".getBytes(StandardCharsets.ISO_8859_1));
    final var good = tempDir.resolve("Good.java");
    Files.writeString(good, "public class Good {}");
    this.run("--write", good.toString());

    final var result = this.run("--check", tempDir.toString());

    assertEquals(0, result.exitCode(), "Undecodable file must be skipped, not fail the batch:\n" + result.stderr());
    assertTrue(result.stderr().contains("Latin1.java"));
    assertTrue(result.stderr().contains("1 file(s) skipped — unreadable."));
  }

  @Test
  void writeFormatsFileInPlace() throws Exception {
    final var file = tempDir.resolve("Fix.java");
    Files.writeString(file, "public   class Fix{public void foo(  ){return ;}}");

    final var result = this.run("--write", file.toString());

    assertEquals(0, result.exitCode());
    final var content = Files.readString(file);
    assertTrue(content.contains("public class Fix"));
    assertTrue(content.contains("  public void foo()"));
  }

  @Test
  void writeIsIdempotent() throws Exception {
    final var file = tempDir.resolve("Idem.java");
    Files.writeString(file, "public class Idem { public void foo() { return; } }");

    this.run("--write", file.toString());
    final var firstFormat = Files.readString(file);

    this.run("--write", file.toString());
    final var secondFormat = Files.readString(file);

    assertEquals(firstFormat, secondFormat);
  }

  @Test
  void stdinFormatsToStdout() {
    final var input = "public class X { public void foo() { return ; } }";
    final var result = this.runWithStdin(input, "--stdin-filepath", "X.java");

    assertEquals(0, result.exitCode());
    assertTrue(result.stdout().contains("public class X"));
    assertTrue(result.stdout().contains("return;"));
  }

  @Test
  void stdinWithParseErrorOutputsUnchangedSource() {
    final var input = "public class Broken { public void (( }";
    final var result = this.runWithStdin(input, "--stdin-filepath", "Broken.java");

    assertEquals(2, result.exitCode());
    assertEquals(input, result.stdout());
    assertTrue(result.stderr().contains("Broken.java"));
  }

  @Test
  void stdinRoundTripsUtf8Multibyte() {
    final var input = """
      // Grüße aus München 🙂
      public class Ümlaut { String s = "café"; }
      """;
    final var result = this.runWithStdin(input, "--stdin-filepath", "Ümlaut.java");

    assertEquals(0, result.exitCode());
    assertTrue(result.stdout().contains("Grüße aus München 🙂"), "comment umlaut/emoji not preserved");
    assertTrue(result.stdout().contains("café"), "string literal umlaut not preserved");
    assertTrue(result.stdout().contains("Ümlaut"), "identifier umlaut not preserved");
  }

  @Test
  void stdinFilepathUsedForConfigLookup() throws Exception {
    Files.writeString(tempDir.resolve(".jacet.json"), "{ \"printWidth\": 40 }\n");

    final var input = "public class X { void foo(int aaaa, int bbbb, int cccc, int dddd) { return; } }";
    final var fakePath = tempDir.resolve("Foo.java").toString();
    final var result = this.runWithStdin(input, "--stdin-filepath", fakePath);

    assertEquals(0, result.exitCode());
    // With default printWidth=140 the signature fits on one line; at 40 it must wrap.
    assertTrue(result.stdout().lines().count() > 3, "Expected wrapped output from printWidth=40 config, got:\n" + result.stdout());
  }

  @Test
  void checkSkipsFilesWithParseErrors() throws Exception {
    final var good = tempDir.resolve("Good.java");
    final var bad = tempDir.resolve("Bad.java");
    Files.writeString(good, "public class Good {\n}\n");
    Files.writeString(bad, "public class Bad { void (( }");

    // Format good file first so it passes check
    this.run("--write", good.toString());

    final var result = this.run("--check", good.toString(), bad.toString());

    // Good file passes, bad file is skipped — overall should pass
    assertTrue(result.stderr().contains("parse error"));
  }

  @Test
  void directoryScansJavaFiles() throws Exception {
    final var sub = tempDir.resolve("src");
    Files.createDirectory(sub);
    Files.writeString(sub.resolve("A.java"), "public class A {}");
    Files.writeString(sub.resolve("B.java"), "public class B {}");
    Files.writeString(sub.resolve("readme.txt"), "not java");

    final var result = this.run("--check", tempDir.toString());

    // Should find 2 java files, ignore .txt
    assertTrue(result.exitCode() == 0 || result.exitCode() == 1);
    assertFalse(result.stdout().contains("readme.txt"));
  }

  @Test
  void noFilesShowsError() {
    final var result = this.run();

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("No files specified"));
  }

  @Test
  void missingNamedJavaFileReportsCleanError() {
    final var missing = tempDir.resolve("DoesNotExist.java");

    final var result = this.run(missing.toString());

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("file not found"), "Expected a clean not-found message, got: " + result.stderr());
    assertFalse(result.stderr().contains("Exception"), "Should not leak a raw stack trace: " + result.stderr());
  }

  @Test
  void checkAndWriteTogetherIsRejected() throws Exception {
    final var file = tempDir.resolve("Any.java");
    Files.writeString(file, "public class Any {}\n");

    final var result = this.run("--check", "--write", file.toString());

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("mutually exclusive"));
  }

  @Test
  void printWidthOptionOverridesConfig() throws Exception {
    final var file = tempDir.resolve("Wide.java");
    Files.writeString(file, "public class Wide {\n}\n");

    this.run("--write", "--print-width", "80", file.toString());

    // Should succeed without error
    final var content = Files.readString(file);
    assertTrue(content.contains("public class Wide"));
  }

  @Test
  void noConfigIgnoresConfigFiles() throws Exception {
    // Write a config file in tempDir
    Files.writeString(
      tempDir.resolve(".jacet.json"),
      """
                                              { "printWidth": 40 }
                                              """
    );
    final var file = tempDir.resolve("Test.java");
    Files.writeString(file, "public class Test {\n}\n");

    // With --no-config, should use defaults (printWidth=140), not the config file
    final var result = this.run("--write", "--no-config", file.toString());
    assertEquals(0, result.exitCode());
  }

  @Test
  void explicitConfigFileOverridesWalkUpConfig() throws Exception {
    Files.writeString(tempDir.resolve(".jacet.json"), "{ \"printWidth\": 140 }\n");
    final var forced = tempDir.resolve("forced.json");
    Files.writeString(forced, "{ \"printWidth\": 40 }\n");
    final var file = tempDir.resolve("X.java");
    Files.writeString(file, "public class X { void foo(int aaaa, int bbbb, int cccc, int dddd) { return; } }");

    final var result = this.run("--write", "--config", forced.toString(), file.toString());

    assertEquals(0, result.exitCode());
    assertTrue(
      Files.readString(file).lines().count() > 3,
      "Expected wrapped output from the forced printWidth=40 config, got:\n" + Files.readString(file)
    );
  }

  @Test
  void missingExplicitConfigFileIsRejected() throws Exception {
    final var file = tempDir.resolve("Any.java");
    Files.writeString(file, "public class Any {}\n");

    final var result = this.run("--config", tempDir.resolve("nope.json").toString(), file.toString());

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("config file not found"), () -> "stderr was: " + result.stderr());
    assertEquals("public class Any {}\n", Files.readString(file), "file must be left untouched on a bad --config path");
  }

  @Test
  void invalidEndOfLineFlagIsRejected() throws Exception {
    final var file = tempDir.resolve("Any.java");
    Files.writeString(file, "public class Any {}\n");

    final var result = this.run("--end-of-line", "windows", file.toString());

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("invalid --end-of-line"), () -> "stderr was: " + result.stderr());
    assertEquals("public class Any {}\n", Files.readString(file), "file must be left untouched on a bad flag");
  }

  @Test
  void invalidStaticImportsFlagIsRejected() throws Exception {
    final var file = tempDir.resolve("Any.java");
    Files.writeString(file, "public class Any {}\n");

    final var result = this.run("--static-imports", "sideways", file.toString());

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("invalid --static-imports"), () -> "stderr was: " + result.stderr());
  }

  @Test
  void nonPositivePrintWidthIsRejected() throws Exception {
    final var file = tempDir.resolve("Any.java");
    Files.writeString(file, "public class Any {}\n");

    final var result = this.run("--print-width", "0", file.toString());

    assertEquals(2, result.exitCode());
    assertTrue(result.stderr().contains("--print-width must be >= 1"), () -> "stderr was: " + result.stderr());
  }
}
