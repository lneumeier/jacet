package de.irotation.jacet;

import java.util.List;

/**
 * Rules for sorting and grouping {@code import} statements.
 *
 * @param staticPosition where static imports appear relative to regular ones
 * @param groups         ordered list of package-prefix groups; non-matching imports fall into an implicit trailing {@code _other} group
 */
public record ImportOptions(StaticPosition staticPosition, List<Group> groups) {

  /**
   * Default rules: static imports at the top, groups ordered {@code java}, {@code javax}, {@code jakarta}, {@code org}, {@code com},
   * {@code de}, {@code lombok}.
   */
  public static ImportOptions defaults() {
    return new ImportOptions(
      StaticPosition.TOP,
      List.of(
        new Group("java"),
        new Group("javax"),
        new Group("jakarta"),
        new Group("org"),
        new Group("com"),
        new Group("de"),
        new Group("lombok")
      )
    );
  }

  /**
   * Placement of static imports relative to regular imports.
   */
  public enum StaticPosition {
    /** Static imports appear above regular imports as a separate block. */
    TOP,
    /** Static imports appear below regular imports as a separate block. */
    BOTTOM,
    /** Static and regular imports are interleaved within each group, sorted together. */
    MIXED,
  }

  /**
   * A single import group, identified by a package-name prefix. An import {@code foo.bar.Baz} falls into the group whose prefix matches
   * {@code foo.bar.Baz} exactly or is a leading dotted prefix of it.
   *
   * @param prefix package-name prefix, e.g. {@code "java"} or {@code "com.example"}
   */
  public record Group(String prefix) {}
}
