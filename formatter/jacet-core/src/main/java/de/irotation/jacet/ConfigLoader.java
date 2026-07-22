package de.irotation.jacet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Loads formatter configuration from {@code .jacet.json} files.
 *
 * <p>Lookup order:
 * <ol>
 *   <li>Given project directory (walks up, stopping at the repository root — see {@link #locate(Path)})</li>
 *   <li>Built-in defaults</li>
 * </ol>
 *
 * <p><b>Parser limits:</b> This is a regex-based extractor, not a full JSON parser. Keys are matched
 * anywhere in the text regardless of JSON structure. Consequences:
 * <ul>
 *   <li>Nested objects are flattened: {@code imports.staticPosition} and {@code imports.groups} are
 *       discovered by their key name alone, no matter how deeply nested.</li>
 *   <li>String literals are not tokenized. A {@code "printWidth": 99} appearing inside another
 *       string value (e.g. an embedded JSON snippet with escaped quotes) would still be picked up.
 *       Natural-language descriptions that happen to mention key names as plain text are safe —
 *       only the literal pattern {@code "keyName"\s*:\s*value} matches.</li>
 *   <li>If the same key appears multiple times, the first match wins.</li>
 * </ul>
 * For real .jacet.json files these limits are not a problem in practice. If a user ever hits one,
 * replace the regex extractor with a small hand-written tokenizer rather than patching the regexes.
 */
public final class ConfigLoader {

  private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());
  private static final String CONFIG_FILE_NAME = ".jacet.json";

  private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("\"([^\"]+)\"");

  private static final Map<String, Pattern> INT_PATTERNS = Map.of(
    "printWidth",
    Pattern.compile("\"printWidth\"\\s*:\\s*(-?\\d+)"),
    "tabWidth",
    Pattern.compile("\"tabWidth\"\\s*:\\s*(-?\\d+)")
  );
  private static final Map<String, Pattern> BOOL_PATTERNS = Map.of(
    "useTabs",
    Pattern.compile("\"useTabs\"\\s*:\\s*(true|false)"),
    "forceBraces",
    Pattern.compile("\"forceBraces\"\\s*:\\s*(true|false)"),
    "removeUnused",
    Pattern.compile("\"removeUnused\"\\s*:\\s*(true|false)")
  );
  private static final Map<String, Pattern> ENUM_PATTERNS = Map.of(
    "endOfLine",
    Pattern.compile("\"endOfLine\"\\s*:\\s*\"(\\w+)\""),
    "staticPosition",
    Pattern.compile("\"staticPosition\"\\s*:\\s*\"(\\w+)\"")
  );
  private static final Pattern GROUPS_PATTERN = Pattern.compile("\"groups\"\\s*:\\s*\\[([^]]*)]");
  private static final List<String> KNOWN_KEYS = List.of(
    "printWidth",
    "tabWidth",
    "useTabs",
    "forceBraces",
    "endOfLine",
    "staticPosition",
    "groups",
    "removeUnused"
  );

  /** Per-key pattern used only to detect whether a present key's value parsed at all, for the typo/wrong-type diagnostics. */
  private static final Map<String, Pattern> VALUE_PATTERNS = Map.ofEntries(
    Map.entry("printWidth", INT_PATTERNS.get("printWidth")),
    Map.entry("tabWidth", INT_PATTERNS.get("tabWidth")),
    Map.entry("useTabs", BOOL_PATTERNS.get("useTabs")),
    Map.entry("forceBraces", BOOL_PATTERNS.get("forceBraces")),
    Map.entry("endOfLine", ENUM_PATTERNS.get("endOfLine")),
    Map.entry("staticPosition", ENUM_PATTERNS.get("staticPosition")),
    Map.entry("groups", GROUPS_PATTERN),
    Map.entry("removeUnused", BOOL_PATTERNS.get("removeUnused"))
  );

  private ConfigLoader() {}

  /**
   * Load configuration, searching from the given directory upward.
   */
  public static FormatterOptions load(@Nullable final Path projectDir) {
    final Path configFile = locate(projectDir);
    return configFile != null ? parseConfigFile(configFile) : FormatterOptions.defaults();
  }

  /**
   * Locate the {@code .jacet.json} that {@link #load(Path)} would parse, without parsing it: walk up from {@code projectDir}, stopping
   * after the first directory that contains a {@code .git} entry (a directory in normal checkouts, a file in worktrees and submodules) —
   * the repository root is the outermost place a project config can live, so a {@code .jacet.json} outside the repository (such as one in
   * the user's home directory) never leaks into a checkout and the same tree formats identically on every machine. Outside any repository
   * the walk continues to the filesystem root. Returns {@code null} when no config file exists in that chain (so the caller uses
   * {@link FormatterOptions#defaults()}); use an explicit config path (CLI {@code --config}, Gradle {@code jacet.configFile}) to format
   * with a file from elsewhere.
   *
   * <p>Exposed so callers that must declare the file as a build input (e.g. the Gradle plugin's up-to-date check)
   * can learn <em>which</em> file backs the configuration, not only the resolved options.
   */
  public static @Nullable Path locate(@Nullable final Path projectDir) {
    if (projectDir == null) {
      return null;
    }
    Path dir = projectDir.toAbsolutePath();
    while (dir != null) {
      final Path configFile = dir.resolve(CONFIG_FILE_NAME);
      if (Files.isRegularFile(configFile)) {
        return configFile;
      }
      if (Files.exists(dir.resolve(".git"))) {
        return null;
      }
      dir = dir.getParent();
    }
    return null;
  }

  /**
   * Read and parse the given config file. Throws {@link UncheckedIOException} if the file cannot be read; malformed or unknown keys in the
   * JSON are silently ignored and fall back to {@link FormatterOptions#defaults()}.
   */
  public static FormatterOptions parseConfigFile(final Path configFile) {
    try {
      final String content = Files.readString(configFile);
      return parseJson(content);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read config file: " + configFile, e);
    }
  }

  static FormatterOptions parseJson(final String json) {
    final FormatterOptions d = FormatterOptions.defaults();
    final FormatterOptions result = new FormatterOptions(
      intValue(json, "printWidth", d.printWidth()),
      intValue(json, "tabWidth", d.tabWidth()),
      boolValue(json, "useTabs", d.useTabs()),
      boolValue(json, "forceBraces", d.forceBraces()),
      enumValue(json, "endOfLine", EndOfLine.class, d.endOfLine()),
      new ImportOptions(
        enumValue(json, "staticPosition", ImportOptions.StaticPosition.class, d.imports().staticPosition()),
        parseGroups(json, d.imports().groups()),
        boolValue(json, "removeUnused", d.imports().removeUnused())
      )
    );

    warnOnUnparsedKeys(json);

    if (result.equals(d) && json.contains(":")) {
      final boolean anyKnownKey = KNOWN_KEYS.stream().anyMatch(k -> json.contains("\"" + k + "\""));
      if (!anyKnownKey) {
        LOGGER.warning("Config file was read but no known keys were matched — check for typos in key names.");
      }
    }

    return result;
  }

  /**
   * Surfaces the silent-degradation case the regex extractor would otherwise hide: a known key that is present in the text but whose value
   * did not match its expected shape (wrong type, e.g. {@code "printWidth": "abc"}, or a malformed value). Such a value is dropped and the
   * default used; without this the user gets no signal at all.
   */
  private static void warnOnUnparsedKeys(final String json) {
    for (final String key : KNOWN_KEYS) {
      if (json.contains("\"" + key + "\"") && !VALUE_PATTERNS.get(key).matcher(json).find()) {
        LOGGER.warning(() -> "Config: value for \"" + key + "\" could not be parsed; using default.");
      }
    }
  }

  private static int intValue(final String json, final String key, final int defaultValue) {
    final Matcher m = INT_PATTERNS.get(key).matcher(json);
    if (!m.find()) {
      return defaultValue;
    }
    final int value;
    try {
      value = Integer.parseInt(m.group(1));
    } catch (final NumberFormatException e) {
      LOGGER.warning(() -> "Config: \"" + key + "\": " + m.group(1) + " is not a valid int value; using default " + defaultValue + ".");
      return defaultValue;
    }
    if (value < 1) {
      LOGGER.warning(() -> "Config: \"" + key + "\": " + value + " is out of range (must be >= 1); using default " + defaultValue + ".");
      return defaultValue;
    }
    return value;
  }

  private static boolean boolValue(final String json, final String key, final boolean defaultValue) {
    final Matcher m = BOOL_PATTERNS.get(key).matcher(json);
    return m.find() ? Boolean.parseBoolean(m.group(1)) : defaultValue;
  }

  private static <E extends Enum<E>> E enumValue(final String json, final String key, final Class<E> enumType, final E defaultValue) {
    final Matcher m = ENUM_PATTERNS.get(key).matcher(json);
    if (m.find()) {
      final String raw = m.group(1);
      try {
        return Enum.valueOf(enumType, raw.toUpperCase(Locale.ROOT));
      } catch (final IllegalArgumentException e) {
        LOGGER.warning(() -> "Config: \"" + key + "\": \"" + raw + "\" is not a valid value; using default " + defaultValue + ".");
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private static List<ImportOptions.Group> parseGroups(final String json, final List<ImportOptions.Group> defaultValue) {
    final Matcher m = GROUPS_PATTERN.matcher(json);
    if (!m.find()) {
      return defaultValue;
    }
    final Matcher items = LIST_ITEM_PATTERN.matcher(m.group(1));
    final List<ImportOptions.Group> groups = new ArrayList<>();
    while (items.find()) {
      groups.add(new ImportOptions.Group(items.group(1)));
    }
    return groups.isEmpty() ? defaultValue : groups;
  }
}
