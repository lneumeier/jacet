package de.irotation.jacet.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JacetPluginTest {

  @TempDir
  Path projectDir;

  @BeforeEach
  void setup() throws IOException {
    Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'test-project'\n");
    Files.writeString(
      projectDir.resolve("build.gradle"),
      """
      plugins {
        id 'java'
        id 'de.irotation.jacet'
      }
      """
    );
    Files.createDirectories(projectDir.resolve("src/main/java"));
  }

  @Test
  void formatTaskFormatsUnformattedFile() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(javaFile, "public class Test { void foo() {  int x=1; } }\n");

    final BuildResult result = runner("formatJava").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":formatJava").getOutcome());
    final String formatted = Files.readString(javaFile);
    assertTrue(formatted.contains("int x = 1;"), "Expected formatted variable declaration");
  }

  @Test
  void formatTaskSkipsAlreadyFormatted() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    // Write already-formatted content
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    final BuildResult result = runner("formatJava").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":formatJava").getOutcome());
    assertTrue(!result.getOutput().contains("Formatted:"), "Should not report any formatted files");
  }

  @Test
  void checkTaskSucceedsForFormattedFile() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    final BuildResult result = runner("checkFormatJava").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":checkFormatJava").getOutcome());
  }

  @Test
  void checkTaskFailsForUnformattedFile() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(javaFile, "public class Test { void foo() {  int x=1; } }\n");

    final BuildResult result = runner("checkFormatJava").buildAndFail();

    assertEquals(TaskOutcome.FAILED, result.task(":checkFormatJava").getOutcome());
    assertTrue(result.getOutput().contains("not formatted"), "Should report unformatted files");
  }

  @Test
  void extensionConfiguresPrintWidth() throws IOException {
    Files.writeString(
      projectDir.resolve("build.gradle"),
      """
      plugins {
        id 'java'
        id 'de.irotation.jacet'
      }
      jacet {
        printWidth = 40
      }
      """
    );
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(javaFile, "public class Test { void foo() { System.out.println(\"hello\"); } }\n");

    final BuildResult result = runner("formatJava").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":formatJava").getOutcome());
    final String formatted = Files.readString(javaFile);
    // With printWidth=40, the method body should be broken across lines
    assertTrue(formatted.contains("\n"), "Expected line breaks with narrow printWidth");
  }

  @Test
  void tasksSucceedWhenNoSources() throws IOException {
    // No Java files in src/main/java — task runs but has nothing to do.
    final BuildResult result = runner("formatJava").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":formatJava").getOutcome());
  }

  @Test
  void formatTaskIsUpToDateWhenSourcesUnchanged() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    final BuildResult first = runner("formatJava").build();
    assertEquals(TaskOutcome.SUCCESS, first.task(":formatJava").getOutcome());

    final BuildResult second = runner("formatJava").build();
    assertEquals(TaskOutcome.UP_TO_DATE, second.task(":formatJava").getOutcome());
  }

  @Test
  void formatTaskReRunsWhenSourceChanges() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    assertEquals(TaskOutcome.SUCCESS, runner("formatJava").build().task(":formatJava").getOutcome());
    assertEquals(TaskOutcome.UP_TO_DATE, runner("formatJava").build().task(":formatJava").getOutcome());

    // Mutate the file — task must re-run and reformat it.
    Files.writeString(javaFile, "public class Test { void foo() {  int x=1; } }\n");

    final BuildResult third = runner("formatJava").build();
    assertEquals(TaskOutcome.SUCCESS, third.task(":formatJava").getOutcome());
    assertTrue(Files.readString(javaFile).contains("int x = 1;"), "File should have been reformatted");
  }

  @Test
  void checkTaskIsUpToDateWhenSourcesUnchanged() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    assertEquals(TaskOutcome.SUCCESS, runner("checkFormatJava").build().task(":checkFormatJava").getOutcome());
    assertEquals(TaskOutcome.UP_TO_DATE, runner("checkFormatJava").build().task(":checkFormatJava").getOutcome());
  }

  @Test
  void checkTaskReRunsWhenSourceChanges() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    assertEquals(TaskOutcome.SUCCESS, runner("checkFormatJava").build().task(":checkFormatJava").getOutcome());

    // Break formatting — task must re-run and now fail.
    Files.writeString(javaFile, "public class Test { void foo() {  int x=1; } }\n");

    final BuildResult second = runner("checkFormatJava").buildAndFail();
    assertEquals(TaskOutcome.FAILED, second.task(":checkFormatJava").getOutcome());
  }

  @Test
  void formatTaskReRunsWhenConfigChanges() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    // useTabs=true should convert this to tab-indented; default is spaces.
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    assertEquals(TaskOutcome.SUCCESS, runner("formatJava").build().task(":formatJava").getOutcome());
    assertEquals(TaskOutcome.UP_TO_DATE, runner("formatJava").build().task(":formatJava").getOutcome());
    assertTrue(Files.readString(javaFile).contains("  int x = 1;"), "Default config should have produced space indentation");

    // Change only the jacet configuration — source file is untouched. Without an
    // input fingerprint on the options, the task would remain UP_TO_DATE here.
    Files.writeString(
      projectDir.resolve("build.gradle"),
      """
      plugins {
        id 'java'
        id 'de.irotation.jacet'
      }
      jacet {
        useTabs = true
      }
      """
    );

    final BuildResult third = runner("formatJava").build();
    assertEquals(TaskOutcome.SUCCESS, third.task(":formatJava").getOutcome());
    assertTrue(Files.readString(javaFile).contains("\tint x = 1;"), "New useTabs=true config should have produced tab indentation");
  }

  @Test
  void formatTaskHandlesParseErrors() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Broken.java");
    final String broken = "public class Broken { void (( }";
    Files.writeString(javaFile, broken);

    final BuildResult result = runner("formatJava").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":formatJava").getOutcome());
    assertEquals(broken, Files.readString(javaFile), "broken file should be left untouched");
    assertTrue(
      result.getOutput().toLowerCase().contains("broken.java"),
      "Expected parse-error warning mentioning the file, got:\n" + result.getOutput()
    );
  }

  @Test
  void checkTaskHandlesParseErrors() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Broken.java");
    Files.writeString(javaFile, "public class Broken { void (( }");

    final BuildResult result = runner("checkFormatJava").build();

    // Parse errors are not format failures — check must succeed.
    assertEquals(TaskOutcome.SUCCESS, result.task(":checkFormatJava").getOutcome());
    assertTrue(
      result.getOutput().toLowerCase().contains("broken.java"),
      "Expected parse-error warning mentioning the file, got:\n" + result.getOutput()
    );
  }

  @Test
  void invalidEndOfLineConfigFailsTheBuild() throws IOException {
    Files.writeString(
      projectDir.resolve("build.gradle"),
      """
      plugins {
        id 'java'
        id 'de.irotation.jacet'
      }
      jacet {
        endOfLine = "windows"
      }
      """
    );
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(javaFile, "public class Test {}\n");

    final BuildResult result = runner("formatJava").buildAndFail();

    assertTrue(
      result.getOutput().contains("invalid endOfLine"),
      "Expected a clear error for the bad endOfLine value, got:\n" + result.getOutput()
    );
  }

  @Test
  void jacetJsonConfiguresFormattingWithoutDslBlock() throws IOException {
    // No jacet { } block in build.gradle (the default from setup) — the .jacet.json must drive formatting.
    Files.writeString(projectDir.resolve(".jacet.json"), "{ \"useTabs\": true }\n");
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    final BuildResult result = runner("formatJava").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":formatJava").getOutcome());
    assertTrue(Files.readString(javaFile).contains("\tint x = 1;"), "useTabs=true from .jacet.json should have produced tab indentation");
  }

  @Test
  void dslBlockOverridesJacetJson() throws IOException {
    // .jacet.json asks for tabs, the DSL overrides it back to spaces — the DSL wins.
    Files.writeString(projectDir.resolve(".jacet.json"), "{ \"useTabs\": true }\n");
    Files.writeString(
      projectDir.resolve("build.gradle"),
      """
      plugins {
        id 'java'
        id 'de.irotation.jacet'
      }
      jacet {
        useTabs = false
      }
      """
    );
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    final BuildResult result = runner("formatJava").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":formatJava").getOutcome());
    assertTrue(
      Files.readString(javaFile).contains("  int x = 1;"),
      "DSL useTabs=false should win over the .jacet.json, producing space indentation"
    );
  }

  @Test
  void explicitConfigFileOverridesWalkUp() throws IOException {
    // A walk-up .jacet.json would give spaces; the explicit configFile gives tabs and must win.
    Files.writeString(projectDir.resolve(".jacet.json"), "{ \"useTabs\": false }\n");
    Files.writeString(projectDir.resolve("cfg.json"), "{ \"useTabs\": true }\n");
    Files.writeString(
      projectDir.resolve("build.gradle"),
      """
      plugins {
        id 'java'
        id 'de.irotation.jacet'
      }
      jacet {
        configFile = file("cfg.json")
      }
      """
    );
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    final BuildResult result = runner("formatJava").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":formatJava").getOutcome());
    assertTrue(Files.readString(javaFile).contains("\tint x = 1;"), "configFile (useTabs=true) should win over the walk-up .jacet.json");
  }

  @Test
  void formatTaskReRunsWhenJacetJsonChanges() throws IOException {
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );
    Files.writeString(projectDir.resolve(".jacet.json"), "{ \"useTabs\": false }\n");

    assertEquals(TaskOutcome.SUCCESS, runner("formatJava").build().task(":formatJava").getOutcome());
    assertEquals(TaskOutcome.UP_TO_DATE, runner("formatJava").build().task(":formatJava").getOutcome());
    assertTrue(Files.readString(javaFile).contains("  int x = 1;"), "useTabs=false should have produced spaces");

    // Change only .jacet.json — the source file is untouched. Without the @InputFile wiring on the
    // config file, the task would remain UP_TO_DATE here.
    Files.writeString(projectDir.resolve(".jacet.json"), "{ \"useTabs\": true }\n");

    final BuildResult third = runner("formatJava").build();
    assertEquals(TaskOutcome.SUCCESS, third.task(":formatJava").getOutcome());
    assertTrue(
      Files.readString(javaFile).contains("\tint x = 1;"),
      "changed .jacet.json (useTabs=true) should have re-run the task and produced tabs"
    );
  }

  @Test
  void checkTaskWorksUnderConfigurationCache() throws IOException {
    // Store + reuse: the task carries the extension in a Property and resolves the config file via providers,
    // both of which would fail the build here if they captured non-serializable configuration state.
    Files.writeString(projectDir.resolve(".jacet.json"), "{ \"useTabs\": false }\n");
    final Path javaFile = projectDir.resolve("src/main/java/Test.java");
    Files.writeString(
      javaFile,
      """
      public class Test {
        void foo() {
          int x = 1;
        }
      }
      """
    );

    final BuildResult first = runner("--configuration-cache", "checkFormatJava").build();
    assertEquals(TaskOutcome.SUCCESS, first.task(":checkFormatJava").getOutcome());

    final BuildResult second = runner("--configuration-cache", "checkFormatJava").build();
    assertTrue(
      second.getOutput().contains("Reusing configuration cache"),
      "Second run should reuse the configuration cache entry, got:\n" + second.getOutput()
    );
    assertEquals(TaskOutcome.UP_TO_DATE, second.task(":checkFormatJava").getOutcome());
  }

  private GradleRunner runner(final String... args) {
    return GradleRunner.create().withProjectDir(projectDir.toFile()).withPluginClasspath().withArguments(args).forwardOutput();
  }
}
