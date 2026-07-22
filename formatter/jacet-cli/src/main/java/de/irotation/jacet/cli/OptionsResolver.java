package de.irotation.jacet.cli;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import de.irotation.jacet.ConfigLoader;
import de.irotation.jacet.EndOfLine;
import de.irotation.jacet.FormatterOptions;
import de.irotation.jacet.ImportOptions;

/**
 * Resolves the effective {@link FormatterOptions} for a run by layering the CLI flags over the configuration file (or built-in defaults). A
 * {@code null} override means "not passed on the command line — keep the configured value"; a non-null override wins. The base configuration
 * is resolved once per invocation, relative to a single reference path (the first path argument, the repository root in the git modes, or
 * the {@code --stdin-filepath} parent) — every file formatted in that invocation shares it.
 */
final class OptionsResolver {

  private final @Nullable Integer printWidth;
  private final @Nullable Integer tabWidth;
  private final @Nullable Boolean useTabs;
  private final @Nullable Boolean forceBraces;
  private final @Nullable String endOfLine;
  private final @Nullable String staticImports;
  private final @Nullable String importGroups;
  private final @Nullable Boolean removeUnusedImports;
  private final @Nullable Path configFile;
  private final boolean noConfig;

  OptionsResolver(
    final @Nullable Integer printWidth,
    final @Nullable Integer tabWidth,
    final @Nullable Boolean useTabs,
    final @Nullable Boolean forceBraces,
    final @Nullable String endOfLine,
    final @Nullable String staticImports,
    final @Nullable String importGroups,
    final @Nullable Boolean removeUnusedImports,
    final @Nullable Path configFile,
    final boolean noConfig
  ) {
    this.printWidth = printWidth;
    this.tabWidth = tabWidth;
    this.useTabs = useTabs;
    this.forceBraces = forceBraces;
    this.endOfLine = endOfLine;
    this.staticImports = staticImports;
    this.importGroups = importGroups;
    this.removeUnusedImports = removeUnusedImports;
    this.configFile = configFile;
    this.noConfig = noConfig;
  }

  /**
   * Validates the command-line overrides up front, independent of the configuration file, so an invalid flag fails fast with
   * a clear message rather than silently falling back to a default or tripping the {@link FormatterOptions} constructor deep
   * in a run. Config-file values are validated separately (and leniently) by {@code ConfigLoader}.
   *
   * @throws IllegalArgumentException if an override is out of range or names an unknown enum value
   */
  void validateOverrides() {
    if (this.printWidth != null && this.printWidth < 1) {
      throw new IllegalArgumentException("--print-width must be >= 1, got " + this.printWidth);
    }
    if (this.tabWidth != null && this.tabWidth < 1) {
      throw new IllegalArgumentException("--tab-width must be >= 1, got " + this.tabWidth);
    }
    parseEndOfLine(this.endOfLine, EndOfLine.LF);
    parseStaticPosition(this.staticImports, ImportOptions.StaticPosition.TOP);
  }

  /** Resolves the options, loading the base configuration relative to {@code reference} (may be {@code null} for stdin). */
  FormatterOptions resolve(final @Nullable Path reference) {
    final FormatterOptions base;
    if (this.noConfig) {
      base = FormatterOptions.defaults();
    } else if (this.configFile != null) {
      base = ConfigLoader.parseConfigFile(this.configFile);
    } else {
      base = ConfigLoader.load(reference);
    }

    return new FormatterOptions(
      this.printWidth != null ? this.printWidth : base.printWidth(),
      this.tabWidth != null ? this.tabWidth : base.tabWidth(),
      this.useTabs != null ? this.useTabs : base.useTabs(),
      this.forceBraces != null ? this.forceBraces : base.forceBraces(),
      parseEndOfLine(this.endOfLine, base.endOfLine()),
      this.buildImportOptions(base.imports())
    );
  }

  private ImportOptions buildImportOptions(final ImportOptions base) {
    final ImportOptions.StaticPosition position = parseStaticPosition(this.staticImports, base.staticPosition());
    final List<ImportOptions.Group> groups = parseImportGroups(this.importGroups, base.groups());
    final boolean removeUnused = this.removeUnusedImports != null ? this.removeUnusedImports : base.removeUnused();
    return new ImportOptions(position, groups, removeUnused);
  }

  private static ImportOptions.StaticPosition parseStaticPosition(
    final @Nullable String value,
    final ImportOptions.StaticPosition defaultValue
  ) {
    if (value == null) {
      return defaultValue;
    }
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "top" -> ImportOptions.StaticPosition.TOP;
      case "bottom" -> ImportOptions.StaticPosition.BOTTOM;
      case "mixed" -> ImportOptions.StaticPosition.MIXED;
      default -> throw new IllegalArgumentException("invalid --static-imports \"" + value + "\" (expected: top, bottom, mixed)");
    };
  }

  private static List<ImportOptions.Group> parseImportGroups(final @Nullable String value, final List<ImportOptions.Group> defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    return Arrays.stream(value.split(","))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .map(ImportOptions.Group::new)
      .toList();
  }

  private static EndOfLine parseEndOfLine(final @Nullable String value, final EndOfLine defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "lf" -> EndOfLine.LF;
      case "crlf" -> EndOfLine.CRLF;
      case "cr" -> EndOfLine.CR;
      case "auto" -> EndOfLine.AUTO;
      default -> throw new IllegalArgumentException("invalid --end-of-line \"" + value + "\" (expected: lf, crlf, cr, auto)");
    };
  }
}
