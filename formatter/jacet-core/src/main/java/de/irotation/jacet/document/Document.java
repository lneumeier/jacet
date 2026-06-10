package de.irotation.jacet.document;

import java.util.ArrayList;
import java.util.List;

/**
 * Intermediate representation for the Wadler-Lindig pretty-printing algorithm. Each variant describes a layout possibility — the printer
 * resolves them to concrete output.
 */
public sealed interface Document
  permits Text, Line, SoftLine, HardLine, Group, Indent, BreakIndent, IndentIfBreak, Concat, IfBreak, LineSuffix, ConditionalLayout
{

  /** Empty document — produces no output. */
  Document EMPTY = new Text("");

  static Document text(final String value) {
    return new Text(value);
  }

  static Document line() {
    return new Line();
  }

  static Document softLine() {
    return new SoftLine();
  }

  static Document hardLine() {
    return new HardLine();
  }

  static Document group(final Document contents) {
    return new Group(contents, containsHardLine(contents));
  }

  /**
   * Reports whether {@code doc}, rendered in FLAT mode, would emit a forced line break. True for {@link HardLine} or for any composite
   * whose children meet that condition. A nested {@link Group} is <em>not</em> traversed: it manages its own flat/break decision, so a
   * hard line inside it does not force the enclosing context to break — use {@link #willBreak} when break propagation across group
   * boundaries is needed. {@link IfBreak} is queried via {@code flatContents} because that's the branch FLAT mode renders.
   * {@link LineSuffix} also forces a break: line suffixes carry trailing line-comments (`// …`) which can never be rendered inline —
   * flattening would either drop the comment (no following newline to flush onto) or swallow everything after the comment as part of it.
   * The enclosing group must therefore commit to BREAK mode. For {@link ConditionalLayout} the primary (optimistic) branch is treated as
   * authoritative: it is the option chosen when the first line fits, so its hard-line content is what this peek reports.
   */
  static boolean containsHardLine(final Document doc) {
    return switch (doc) {
      case HardLine _ -> true;
      case final Group _ -> false;
      case final Concat c -> c.parts().stream().anyMatch(Document::containsHardLine);
      case final Indent i -> containsHardLine(i.contents());
      case final BreakIndent bi -> containsHardLine(bi.contents());
      case final IndentIfBreak iib -> containsHardLine(iib.contents());
      case final ConditionalLayout cl -> containsHardLine(cl.primary());
      case final IfBreak ib -> containsHardLine(ib.flatContents());
      case LineSuffix _ -> true;
      case Text _, Line _, SoftLine _ -> false;
    };
  }

  /**
   * Like {@link #containsHardLine}, but propagates through nested {@link Group}s: a group reports as breaking when its own
   * {@code shouldBreak} flag is set or its contents would break. This mirrors prettier's {@code willBreak}, which follows break
   * propagation across group boundaries. Member-chain layout uses it to expand the whole chain when a non-last segment will break
   * (a hard-breaking lambda block, a wrapped argument list, ...), where {@link #containsHardLine} would stop at the segment's own group.
   */
  static boolean willBreak(final Document doc) {
    return switch (doc) {
      case HardLine _ -> true;
      case final Group g -> g.shouldBreak() || willBreak(g.contents());
      case final Concat c -> c.parts().stream().anyMatch(Document::willBreak);
      case final Indent i -> willBreak(i.contents());
      case final BreakIndent bi -> willBreak(bi.contents());
      case final IndentIfBreak iib -> willBreak(iib.contents());
      case final ConditionalLayout cl -> willBreak(cl.primary());
      case final IfBreak ib -> willBreak(ib.flatContents());
      case LineSuffix _ -> true;
      case Text _, Line _, SoftLine _ -> false;
    };
  }

  /**
   * Computes the character width of {@code doc} when rendered in FLAT mode (Lines → space, SoftLines → nothing). Returns {@code -1} if the
   * document cannot be rendered flat (contains HardLine or a Group with shouldBreak).
   */
  static int flatWidth(final Document doc) {
    return switch (doc) {
      case final Text t -> t.value().length();
      case Line _ -> 1;
      case SoftLine _ -> 0;
      case HardLine _ -> -1;
      case final Group g -> g.shouldBreak() ? -1 : flatWidth(g.contents());
      case final Concat c -> {
        int w = 0;
        for (final Document p : c.parts()) {
          final int pw = flatWidth(p);
          if (pw < 0) {
            yield -1;
          }
          w += pw;
        }
        yield w;
      }
      case final Indent i -> flatWidth(i.contents());
      case final BreakIndent bi -> flatWidth(bi.contents());
      case final IndentIfBreak iib -> flatWidth(iib.contents());
      case final ConditionalLayout cl -> flatWidth(cl.primary());
      case final IfBreak ib -> flatWidth(ib.flatContents());
      case LineSuffix _ -> 0;
    };
  }

  /**
   * Returns a copy of {@code doc} with every soft break removed: {@link Line} becomes a single space, {@link SoftLine} vanishes, and an
   * {@link IfBreak} collapses to its flat branch. {@link HardLine} and {@link LineSuffix} are preserved (they can never render inline).
   * Mirrors prettier's {@code removeLines}; used to force a sub-document flat so a surrounding {@code strictConditionalLayout} measures its
   * true single-line width — e.g. lambda parameters in the last-arg hug layout, which must not break to make the signature appear to fit.
   */
  static Document removeLines(final Document doc) {
    return switch (doc) {
      case Line _ -> new Text(" ");
      case SoftLine _ -> EMPTY;
      case final IfBreak ib -> removeLines(ib.flatContents());
      case final Group g -> new Group(removeLines(g.contents()), g.shouldBreak());
      case final Concat c -> new Concat(c.parts().stream().map(Document::removeLines).toList());
      case final Indent i -> new Indent(removeLines(i.contents()));
      case final BreakIndent bi -> new BreakIndent(removeLines(bi.contents()));
      case final IndentIfBreak iib -> new IndentIfBreak(iib.group(), removeLines(iib.contents()));
      case final ConditionalLayout cl -> new ConditionalLayout(removeLines(cl.primary()), removeLines(cl.fallback()), cl.strict());
      case final LineSuffix ls -> new LineSuffix(removeLines(ls.contents()));
      case Text _, HardLine _ -> doc;
    };
  }

  static Document indent(final Document contents) {
    return new Indent(contents);
  }

  static Document breakIndent(final Document contents) {
    return new BreakIndent(contents);
  }

  static Document indentIfBreak(final Group group, final Document contents) {
    return new IndentIfBreak(group, contents);
  }

  static Document concat(final Document... parts) {
    return new Concat(List.of(parts));
  }

  static Document concat(final List<Document> parts) {
    return new Concat(parts);
  }

  static Document ifBreak(final Document breakContents, final Document flatContents) {
    return new IfBreak(breakContents, flatContents);
  }

  static Document lineSuffix(final Document contents) {
    return new LineSuffix(contents);
  }

  static Document conditionalLayout(final Document primary, final Document fallback) {
    return new ConditionalLayout(primary, fallback, false);
  }

  static Document strictConditionalLayout(final Document primary, final Document fallback) {
    return new ConditionalLayout(primary, fallback, true);
  }

  /** Join a list of documents with a separator between each. */
  static Document join(final Document separator, final List<Document> documents) {
    if (documents.isEmpty()) {
      return EMPTY;
    }
    if (documents.size() == 1) {
      return documents.getFirst();
    }
    final List<Document> parts = new ArrayList<>();
    for (int i = 0; i < documents.size(); i++) {
      if (i > 0) {
        parts.add(separator);
      }
      parts.add(documents.get(i));
    }
    return new Concat(parts);
  }

  static Document empty() {
    return EMPTY;
  }
}
