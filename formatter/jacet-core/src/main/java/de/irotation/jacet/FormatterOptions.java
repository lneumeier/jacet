package de.irotation.jacet;

/**
 * Configuration for {@link JacetFormatter}. Instances are immutable; obtain a starting point via {@link #defaults()} and construct a new
 * record with adjusted fields.
 *
 * @param printWidth  target line width in characters — the printer breaks groups that would exceed it
 * @param tabWidth    indentation width per level in characters (also the tab stop when {@code useTabs} is true)
 * @param useTabs     {@code true} to indent with {@code \t} characters, {@code false} for spaces
 * @param forceBraces  wrap single-statement {@code if}/{@code for}/{@code while} bodies in braces
 * @param endOfLine    line ending policy; {@link EndOfLine#AUTO} picks from the input
 * @param imports      import sorting and grouping rules, see {@link ImportOptions}
 */
public record FormatterOptions(
  int printWidth,
  int tabWidth,
  boolean useTabs,
  boolean forceBraces,
  EndOfLine endOfLine,
  ImportOptions imports
) {

  /**
   * Validates the numeric invariants every construction path relies on: a non-positive {@code printWidth} would make the
   * printer break every group, and a non-positive {@code tabWidth} would corrupt indentation. Callers that accept untrusted
   * input (CLI flags, {@code .jacet.json}) validate earlier with a friendlier message; this is the universal backstop so the
   * printer can trust its inputs.
   *
   * @throws IllegalArgumentException if {@code printWidth < 1} or {@code tabWidth < 1}
   */
  public FormatterOptions {
    if (printWidth < 1) {
      throw new IllegalArgumentException("printWidth must be >= 1, got " + printWidth);
    }
    if (tabWidth < 1) {
      throw new IllegalArgumentException("tabWidth must be >= 1, got " + tabWidth);
    }
  }

  /**
   * Default configuration: 140-char lines, 2-space indentation, LF line endings, force braces on,
   * {@link ImportOptions#defaults()}.
   */
  public static FormatterOptions defaults() {
    return new FormatterOptions(140, 2, false, true, EndOfLine.LF, ImportOptions.defaults());
  }
}
