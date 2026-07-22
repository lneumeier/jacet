package de.irotation.jacet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

class ConfigLoaderTest {

  @Test
  void parsesCompleteConfig() {
    final var json = """
                     {
                       "printWidth": 100,
                       "tabWidth": 2,
                       "useTabs": true,
                       "endOfLine": "CRLF",
                       "imports": {
                         "staticPosition": "TOP",
                         "groups": ["org", "com", "java"],
                         "removeUnused": false
                       }
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(100, options.printWidth());
    assertEquals(2, options.tabWidth());
    assertTrue(options.useTabs());
    assertEquals(EndOfLine.CRLF, options.endOfLine());
    assertEquals(ImportOptions.StaticPosition.TOP, options.imports().staticPosition());
    assertEquals(3, options.imports().groups().size());
    assertEquals("org", options.imports().groups().getFirst().prefix());
    assertFalse(options.imports().removeUnused());
  }

  @Test
  void defaultsForMissingValues() {
    final var json = """
                     {
                       "printWidth": 80
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(80, options.printWidth());
    assertEquals(2, options.tabWidth()); // default
    assertFalse(options.useTabs()); // default
    assertEquals(EndOfLine.LF, options.endOfLine()); // default
  }

  @Test
  void emptyJsonUsesAllDefaults() {
    final var options = ConfigLoader.parseJson("{}");

    assertEquals(140, options.printWidth());
    assertEquals(2, options.tabWidth());
    assertFalse(options.useTabs());
    assertEquals(EndOfLine.LF, options.endOfLine());
    assertEquals(ImportOptions.StaticPosition.TOP, options.imports().staticPosition());
    assertTrue(options.imports().removeUnused());
  }

  @Test
  void loadReturnsDefaultsWhenNoConfigFile() {
    final var options = ConfigLoader.load(null);
    assertEquals(140, options.printWidth());
  }

  @Test
  void locateFindsConfigInProjectDir(@TempDir final Path projectDir) throws Exception {
    final Path config = projectDir.resolve(".jacet.json");
    Files.writeString(config, "{ \"printWidth\": 60 }\n");

    assertEquals(config, ConfigLoader.locate(projectDir));
  }

  @Test
  void locateWalksUpToParentDir(@TempDir final Path projectDir) throws Exception {
    final Path config = projectDir.resolve(".jacet.json");
    Files.writeString(config, "{ \"printWidth\": 60 }\n");
    final Path nested = Files.createDirectories(projectDir.resolve("module/src/main/java"));

    assertEquals(config, ConfigLoader.locate(nested));
  }

  @Test
  void locateStopsAtGitRepositoryRoot(@TempDir final Path tree) throws Exception {
    // A .jacet.json above the repository root must never leak into the checkout — the same tree has to
    // format identically on every machine, regardless of what lives outside the repository.
    Files.writeString(tree.resolve(".jacet.json"), "{ \"printWidth\": 77 }\n");
    final Path repoRoot = Files.createDirectories(tree.resolve("repo"));
    Files.createDirectories(repoRoot.resolve(".git"));
    final Path nested = Files.createDirectories(repoRoot.resolve("src/main/java"));

    assertNull(ConfigLoader.locate(nested), "the walk-up must stop at the repository root");
  }

  @Test
  void locateFindsConfigAtGitRepositoryRoot(@TempDir final Path repoRoot) throws Exception {
    Files.createDirectories(repoRoot.resolve(".git"));
    final Path config = repoRoot.resolve(".jacet.json");
    Files.writeString(config, "{ \"printWidth\": 60 }\n");
    final Path nested = Files.createDirectories(repoRoot.resolve("src/main/java"));

    assertEquals(config, ConfigLoader.locate(nested), "the repository root itself is still searched");
  }

  @Test
  void locateStopsAtGitFileBoundary(@TempDir final Path tree) throws Exception {
    // Worktrees and submodules mark their root with a .git *file*; it bounds the walk just like a .git directory.
    Files.writeString(tree.resolve(".jacet.json"), "{ \"printWidth\": 77 }\n");
    final Path worktree = Files.createDirectories(tree.resolve("wt"));
    Files.writeString(worktree.resolve(".git"), "gitdir: /elsewhere\n");

    assertNull(ConfigLoader.locate(worktree));
  }

  @Test
  @ResourceLock(Resources.SYSTEM_PROPERTIES)
  void locateNeverFallsBackToHomeDir(@TempDir final Path projectDir, @TempDir final Path homeDir) throws Exception {
    Files.writeString(homeDir.resolve(".jacet.json"), "{ \"printWidth\": 77 }\n");
    final String originalHome = System.getProperty("user.home");
    try {
      System.setProperty("user.home", homeDir.toString());
      assertNull(ConfigLoader.locate(projectDir), "a home-dir .jacet.json must not be picked up");
      assertNull(ConfigLoader.locate(null), "stdin without a reference path must resolve to defaults, not home");
    } finally {
      System.setProperty("user.home", originalHome);
    }
  }

  @Test
  void locateReturnsNullWhenNoConfigAnywhere(@TempDir final Path projectDir) {
    assertNull(ConfigLoader.locate(projectDir), "no config in the project tree should resolve to null");
  }

  @Test
  void invalidEnumValueFallsBackToDefault() {
    final var json = """
                     {
                       "endOfLine": "INVALID"
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(EndOfLine.LF, options.endOfLine());
  }

  @Test
  void outOfRangeIntValuesFallBackToDefault() {
    // Zero/negative printWidth or tabWidth are never meaningful (they would break the printer); the loader must
    // reject them and keep the defaults rather than propagate a value the FormatterOptions constructor would refuse.
    final var json = """
                     {
                       "printWidth": -1,
                       "tabWidth": 0
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(140, options.printWidth());
    assertEquals(2, options.tabWidth());
  }

  @Test
  void intValuesBeyondIntRangeFallBackToDefault() {
    // The int regex accepts unbounded digit runs, so an overflowing literal must degrade to the default with a
    // warning instead of escaping as a NumberFormatException that would kill every CLI run and Gradle task.
    final var json = """
                     {
                       "printWidth": 99999999999,
                       "tabWidth": -99999999999
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(140, options.printWidth());
    assertEquals(2, options.tabWidth());
  }

  @Test
  void wrongTypedValueForKnownKeyFallsBackToDefault() {
    // A present-but-unparseable value (wrong type) must not be silently honored as anything other than the default.
    final var json = """
                     {
                       "printWidth": "abc"
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(140, options.printWidth());
  }

  @Test
  void emptyStringUsesAllDefaults() {
    final var options = ConfigLoader.parseJson("");

    assertEquals(140, options.printWidth());
    assertFalse(options.useTabs());
    assertEquals(EndOfLine.LF, options.endOfLine());
  }

  @Test
  void emptyGroupsArrayUsesDefaults() {
    final var json = """
                     {
                       "imports": {
                         "groups": []
                       }
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(ImportOptions.defaults().groups(), options.imports().groups());
  }

  @Test
  void caseInsensitiveEnumParsing() {
    final var json = """
                     {
                       "endOfLine": "crlf"
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(EndOfLine.CRLF, options.endOfLine());
  }

  @Test
  void forceBracesIsParsed() {
    final var json = """
                     {
                       "forceBraces": false
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertFalse(options.forceBraces());
  }

  @Test
  void malformedJsonFallsBackToDefaults() {
    // Missing closing brace, unmatched quote — regex parser must not throw
    final var json = """
                     {
                       "printWidth": 100,
                       "useTabs": true
                     """;

    final var options = ConfigLoader.parseJson(json);

    // Values that match their patterns should still be extracted
    assertEquals(100, options.printWidth());
    assertTrue(options.useTabs());
  }

  @Test
  void garbageInputFallsBackToDefaults() {
    final var options = ConfigLoader.parseJson("not actually json at all }");

    assertEquals(140, options.printWidth());
    assertFalse(options.useTabs());
    assertEquals(EndOfLine.LF, options.endOfLine());
  }

  @Test
  void escapedQuotesInStringValueAreNotMisParsed() {
    // Unknown string key with an escaped quote — must not break parsing of known keys
    final var json = """
                     {
                       "someNote": "he said \\"hello\\"",
                       "printWidth": 99
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(99, options.printWidth());
  }

  @Test
  void naturalLanguageMentioningKeyNameDoesNotLeakIntoConfig() {
    // Regex extractor requires literal "keyName": <value>. A plain-English description mentioning
    // "printWidth" inside an unknown string value must not override the real printWidth.
    final var json = """
                     {
                       "description": "lowering printWidth below 40 is not recommended",
                       "printWidth": 100
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(100, options.printWidth());
  }

  @Test
  void extraWhitespaceInJsonIsTolerated() {
    final var json = """
                     {
                       "printWidth"   :    80  ,
                       "useTabs"  :  true
                     }
                     """;

    final var options = ConfigLoader.parseJson(json);

    assertEquals(80, options.printWidth());
    assertTrue(options.useTabs());
  }
}
