package de.irotation.jacet.gradle;

import java.util.List;
import java.util.Locale;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import de.irotation.jacet.EndOfLine;
import de.irotation.jacet.FormatterOptions;
import de.irotation.jacet.ImportOptions;

/**
 * DSL extension for configuring the jacet formatter.
 *
 * <p>Configuration is layered, highest priority last: built-in {@link FormatterOptions#defaults()} &lt;
 * {@code .jacet.json} &lt; this {@code jacet { }} block. The plugin loads {@code .jacet.json} by walking up from the
 * project directory (mirroring the CLI); any property set here overrides the file. An unset property falls through to
 * the file value, then to the default shown below. The string-valued properties accept a fixed vocabulary and fail the
 * build with a clear message on any other value (see {@link #applyOverrides}).
 *
 * <p>Use {@code configFile} to point at a specific {@code .jacet.json} instead of the walk-up lookup (the counterpart
 * of the CLI's {@code --config}).
 *
 * <pre>
 * jacet {
 *   configFile = file("config/.jacet.json")                  // optional: skip walk-up, use this file
 *   printWidth = 140                                          // line width, default 140
 *   tabWidth = 2                                              // indent width, default 2
 *   useTabs = false                                           // indent with tabs, default false
 *   forceBraces = true                                        // brace single-statement bodies, default true
 *   endOfLine = "lf"                                          // one of: lf, crlf, cr, auto; default lf
 *   staticImports = "top"                                     // one of: top, bottom, mixed; default top
 *   importGroups = ["java", "javax", "jakarta", "org", "com", "de", "lombok"] // ordered group prefixes
 * }
 * </pre>
 */
public abstract class JacetExtension {

  public abstract RegularFileProperty getConfigFile();

  public abstract Property<Integer> getPrintWidth();

  public abstract Property<Integer> getTabWidth();

  public abstract Property<Boolean> getUseTabs();

  public abstract Property<Boolean> getForceBraces();

  public abstract Property<String> getEndOfLine();

  public abstract Property<String> getStaticImports();

  public abstract ListProperty<String> getImportGroups();

  /**
   * Layer the DSL properties over {@code base}: each property that was set in the {@code jacet { }} block wins,
   * each unset one falls through to the matching {@code base} value. {@code base} is the configuration loaded from
   * {@code .jacet.json} (or {@link FormatterOptions#defaults()} when no file applies).
   */
  FormatterOptions applyOverrides(final FormatterOptions base) {
    final int printWidth = this.getPrintWidth().getOrElse(base.printWidth());
    final int tabWidth = this.getTabWidth().getOrElse(base.tabWidth());
    final boolean useTabs = this.getUseTabs().getOrElse(base.useTabs());
    final boolean forceBraces = this.getForceBraces().getOrElse(base.forceBraces());

    final EndOfLine endOfLine = this.getEndOfLine()
      .map(s ->
        switch (s.toLowerCase(Locale.ROOT)) {
          case "lf" -> EndOfLine.LF;
          case "crlf" -> EndOfLine.CRLF;
          case "cr" -> EndOfLine.CR;
          case "auto" -> EndOfLine.AUTO;
          default -> throw new IllegalArgumentException("jacet: invalid endOfLine \"" + s + "\" (expected: lf, crlf, cr, auto)");
        }
      )
      .getOrElse(base.endOfLine());

    final ImportOptions.StaticPosition staticPosition = this.getStaticImports()
      .map(s ->
        switch (s.toLowerCase(Locale.ROOT)) {
          case "top" -> ImportOptions.StaticPosition.TOP;
          case "bottom" -> ImportOptions.StaticPosition.BOTTOM;
          case "mixed" -> ImportOptions.StaticPosition.MIXED;
          default -> throw new IllegalArgumentException("jacet: invalid staticImports \"" + s + "\" (expected: top, bottom, mixed)");
        }
      )
      .getOrElse(base.imports().staticPosition());

    final List<ImportOptions.Group> groups;
    if (this.getImportGroups().isPresent() && !this.getImportGroups().get().isEmpty()) {
      groups = this.getImportGroups().get().stream().map(ImportOptions.Group::new).toList();
    } else {
      groups = base.imports().groups();
    }

    return new FormatterOptions(printWidth, tabWidth, useTabs, forceBraces, endOfLine, new ImportOptions(staticPosition, groups));
  }
}
