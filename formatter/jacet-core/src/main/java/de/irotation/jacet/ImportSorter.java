package de.irotation.jacet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sorts and groups Java import statements according to the configured rules.
 */
public final class ImportSorter {

  private static final String OTHER_GROUP = "_other";
  private static final Comparator<ImportStatement> BY_NAME = Comparator.comparing(ImportStatement::qualifiedName);

  private ImportSorter() {}

  /**
   * Sort and group imports according to the given options.
   *
   * @param imports the parsed import statements
   * @param options the import sorting configuration
   * @return formatted import block as a string (with line endings)
   */
  public static String sort(final List<ImportStatement> imports, final ImportOptions options, final String endOfLine) {
    if (imports.isEmpty()) {
      return "";
    }

    final List<ImportStatement> staticImports = imports.stream().filter(ImportStatement::isStatic).sorted(BY_NAME).toList();
    final List<ImportStatement> regularImports = imports
      .stream()
      .filter(i -> !i.isStatic())
      .sorted(BY_NAME)
      .toList();

    final StringBuilder result = new StringBuilder();

    switch (options.staticPosition()) {
      case TOP -> {
        appendFlat(result, staticImports, endOfLine);
        if (!staticImports.isEmpty() && !regularImports.isEmpty()) {
          result.append(endOfLine);
        }
        appendGrouped(result, regularImports, options.groups(), endOfLine);
      }
      case BOTTOM -> {
        appendGrouped(result, regularImports, options.groups(), endOfLine);
        if (!regularImports.isEmpty() && !staticImports.isEmpty()) {
          result.append(endOfLine);
        }
        appendFlat(result, staticImports, endOfLine);
      }
      case MIXED -> {
        final List<ImportStatement> all = new ArrayList<>(imports);
        all.sort(BY_NAME);
        appendGrouped(result, all, options.groups(), endOfLine);
      }
    }

    return result.toString();
  }

  /**
   * Parse import statements from raw source lines, tracking the region boundaries. Reads from a {@link StringReader}, so the declared
   * {@link IOException} can never actually occur; it is rewrapped as {@link UncheckedIOException} purely to satisfy the checked-exception
   * contract.
   */
  public static ImportRegion parseRegion(final String source) {
    final List<ImportStatement> imports = new ArrayList<>();
    int startLine = -1;
    int endLine = -1;
    int lineNumber = 0;

    try (final BufferedReader reader = new BufferedReader(new StringReader(source))) {
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        final String trimmed = line.trim();
        if (trimmed.startsWith("import ")) {
          if (startLine < 0) {
            startLine = lineNumber;
          }
          endLine = lineNumber;
          final boolean isStatic = trimmed.startsWith("import static ");
          final int nameStart = isStatic ? "import static ".length() : "import ".length();
          final int semi = trimmed.indexOf(';');
          final int nameEnd = semi < 0 ? trimmed.length() : semi;
          final String qualifiedName = trimmed.substring(nameStart, nameEnd).trim();
          imports.add(new ImportStatement(qualifiedName, isStatic));
        } else if (!isPreambleLine(trimmed)) {
          break;
        }
        lineNumber++;
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return new ImportRegion(imports, startLine, endLine);
  }

  /**
   * Parse import statements from raw source lines.
   */
  public static List<ImportStatement> parse(final String source) {
    return parseRegion(source).imports();
  }

  /**
   * Append all imports as a single block without sub-grouping. Only called with the already-filtered static imports list.
   */
  private static void appendFlat(final StringBuilder result, final Iterable<ImportStatement> imports, final String endOfLine) {
    for (final ImportStatement imp : imports) {
      result.append("import static ");
      result.append(imp.qualifiedName());
      result.append(";");
      result.append(endOfLine);
    }
  }

  /**
   * Group imports by their prefix and append to the result with blank lines between groups.
   */
  private static void appendGrouped(
    final StringBuilder result,
    final Collection<ImportStatement> imports,
    final Iterable<ImportOptions.Group> groups,
    final String endOfLine
  ) {
    if (imports.isEmpty()) {
      return;
    }

    final Map<String, List<ImportStatement>> grouped = new LinkedHashMap<>();
    for (final ImportOptions.Group group : groups) {
      grouped.put(group.prefix(), new ArrayList<>());
    }
    grouped.put(OTHER_GROUP, new ArrayList<>());

    for (final ImportStatement imp : imports) {
      boolean assigned = false;
      for (final ImportOptions.Group group : groups) {
        if (imp.qualifiedName().startsWith(group.prefix() + ".") || imp.qualifiedName().equals(group.prefix())) {
          grouped.get(group.prefix()).add(imp);
          assigned = true;
          break;
        }
      }
      if (!assigned) {
        grouped.get(OTHER_GROUP).add(imp);
      }
    }

    boolean firstGroup = true;
    for (final Map.Entry<String, List<ImportStatement>> entry : grouped.entrySet()) {
      final List<ImportStatement> groupImports = entry.getValue();
      if (groupImports.isEmpty()) {
        continue;
      }

      if (!firstGroup) {
        result.append(endOfLine);
      }
      firstGroup = false;

      groupImports.sort(BY_NAME);
      for (final ImportStatement imp : groupImports) {
        result.append(imp.isStatic() ? "import static " : "import ");
        result.append(imp.qualifiedName());
        result.append(";");
        result.append(endOfLine);
      }
    }
  }

  /**
   * Returns true for lines that may legally precede or appear between imports in a compilation unit's preamble: blank lines, line and block
   * comments (including continuations), the package declaration, and annotations. Anything else — most notably a type-declaration or field
   * initializer — terminates the import scan, so that text-block content or code that happens to contain the literal "import " cannot be
   * adopted as an import.
   */
  private static boolean isPreambleLine(final String trimmed) {
    return (
      trimmed.isEmpty() ||
      trimmed.startsWith("//") ||
      trimmed.startsWith("/*") ||
      trimmed.startsWith("*") ||
      trimmed.startsWith("package ") ||
      trimmed.startsWith("@")
    );
  }

  /**
   * Represents a single import statement.
   */
  public record ImportStatement(String qualifiedName, boolean isStatic) {}

  /**
   * Result of parsing the import region, including line positions.
   */
  public record ImportRegion(List<ImportStatement> imports, int startLine, int endLine) {

    public ImportRegion {
      imports = List.copyOf(imports);
    }
  }
}
