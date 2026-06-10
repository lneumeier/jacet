package de.irotation.jacet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.irotation.jacet.document.BreakIndent;
import de.irotation.jacet.document.Concat;
import de.irotation.jacet.document.ConditionalLayout;
import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Group;
import de.irotation.jacet.document.HardLine;
import de.irotation.jacet.document.IfBreak;
import de.irotation.jacet.document.Indent;
import de.irotation.jacet.document.IndentIfBreak;
import de.irotation.jacet.document.Line;
import de.irotation.jacet.document.LineSuffix;
import de.irotation.jacet.document.SoftLine;
import de.irotation.jacet.document.Text;

/**
 * Wadler-Lindig pretty-printer. Resolves a Document IR tree into a formatted string.
 *
 * <p>Algorithm: stack-based, left-to-right, linear time.
 * Each {@link Group} is tested with {@code fits()} — if the flat rendering fits within the remaining line width, it is printed flat;
 * otherwise it breaks.
 */
public final class DocumentPrinter {

  private final int printWidth;
  private final int tabWidth;
  private final boolean useTabs;
  private final String endOfLine;
  private final Map<Integer, String> indentCache = new HashMap<>();

  public DocumentPrinter(final int printWidth, final int tabWidth, final boolean useTabs, final String endOfLine) {
    this.printWidth = printWidth;
    this.tabWidth = tabWidth;
    this.useTabs = useTabs;
    this.endOfLine = endOfLine;
  }

  /**
   * Tracks each Group's flat/break decision during rendering so {@link IndentIfBreak} siblings (which sit outside the group) can react.
   * Identity-keyed because Group instances are the canonical reference. Reset at the start of every {@link #print} call.
   */
  private final Map<Group, Boolean> groupBroken = new IdentityHashMap<>();

  /**
   * Print the given Document IR to a formatted string. Any line suffixes still queued when rendering finishes are flushed at the end, with
   * no trailing newline.
   */
  public String print(final Document document) {
    groupBroken.clear();
    final StringBuilder output = new StringBuilder();
    final List<StackEntry> lineSuffixes = new ArrayList<>();
    this.render(document, 0, Mode.BREAK, 0, output, lineSuffixes);

    for (final StackEntry suffix : lineSuffixes) {
      this.render(suffix.document(), suffix.indent(), suffix.mode(), 0, output, new ArrayList<>());
    }

    return output.toString();
  }

  /**
   * Render a document to the output builder. Returns the column position after rendering.
   *
   * <p>Per-variant notes:
   * <ul>
   *   <li><b>Text:</b> a multi-line text (a text-block literal carries its source newlines verbatim) ends on
   *       its last line, so the running column resets to that line's width rather than growing by the whole
   *       literal — otherwise a trailing {@code .method(...)} would measure from an inflated column.</li>
   *   <li><b>IndentIfBreak:</b> looks up the referenced group's break decision recorded earlier in this
   *       render. If the group hasn't been processed yet (forward reference) or its decision is missing, it
   *       defaults to no extra indent — the caller is expected to place the IndentIfBreak after the
   *       referenced group in the IR.</li>
   *   <li><b>ConditionalLayout (non-strict):</b> prettier's conditionalGroup — choose the primary iff it
   *       fits FLAT, where a forced break (a hard line, e.g. a trailing block lambda) ends the measured first
   *       line and still counts as a fit; otherwise fall back. The chosen layout renders in the entry's own
   *       mode, so a block lambda inside the primary still breaks while the surrounding form stays linear.</li>
   * </ul>
   */
  private int render(
    final Document document,
    final int indent,
    final Mode mode,
    final int column,
    final StringBuilder output,
    final List<StackEntry> lineSuffixes
  ) {
    final ArrayDeque<StackEntry> stack = new ArrayDeque<>();
    stack.push(new StackEntry(indent, mode, document));
    int col = column;

    while (!stack.isEmpty()) {
      final StackEntry entry = stack.pop();

      switch (entry.document()) {
        case final Text t -> {
          output.append(t.value());
          final int lastBreak = lastLineBreak(t.value());
          col = lastBreak < 0 ? col + t.value().length() : t.value().length() - lastBreak - 1;
        }
        case final Concat c -> {
          final List<Document> parts = c.parts();
          for (int i = parts.size() - 1; i >= 0; i--) {
            stack.push(new StackEntry(entry.indent(), entry.mode(), parts.get(i)));
          }
        }
        case final Line _ -> {
          if (entry.mode() == Mode.FLAT) {
            output.append(' ');
            col += 1;
          } else {
            col = this.flushLineSuffixesAndNewline(lineSuffixes, output, entry.indent());
          }
        }
        case final SoftLine _ -> {
          if (entry.mode() != Mode.FLAT) {
            col = this.flushLineSuffixesAndNewline(lineSuffixes, output, entry.indent());
          }
        }
        case final HardLine _ -> col = this.flushLineSuffixesAndNewline(lineSuffixes, output, entry.indent());
        case final Group g -> {
          final boolean flat =
            !g.shouldBreak() &&
            (entry.mode() == Mode.FLAT || this.fits(printWidth - col, new StackEntry(entry.indent(), Mode.FLAT, g.contents()), stack));
          groupBroken.put(g, !flat);
          stack.push(new StackEntry(entry.indent(), flat ? Mode.FLAT : Mode.BREAK, g.contents()));
        }
        case final Indent i -> stack.push(new StackEntry(entry.indent() + tabWidth, entry.mode(), i.contents()));
        case final BreakIndent bi -> {
          final int newIndent = entry.mode() == Mode.BREAK ? entry.indent() + tabWidth : entry.indent();
          stack.push(new StackEntry(newIndent, entry.mode(), bi.contents()));
        }
        case final IndentIfBreak iib -> {
          final boolean broke = Boolean.TRUE.equals(groupBroken.get(iib.group()));
          final int newIndent = broke ? entry.indent() + tabWidth : entry.indent();
          stack.push(new StackEntry(newIndent, entry.mode(), iib.contents()));
        }
        case final IfBreak ib -> {
          if (entry.mode() == Mode.BREAK) {
            stack.push(new StackEntry(entry.indent(), entry.mode(), ib.breakContents()));
          } else {
            stack.push(new StackEntry(entry.indent(), entry.mode(), ib.flatContents()));
          }
        }
        case final LineSuffix ls -> lineSuffixes.add(new StackEntry(entry.indent(), entry.mode(), ls.contents()));
        case final ConditionalLayout cl -> {
          if (cl.strict()) {
            final Map<Group, Boolean> savedGroupState = new IdentityHashMap<>(groupBroken);
            final StringBuilder probe = new StringBuilder();
            final List<StackEntry> probeSuffixes = new ArrayList<>();
            final int endCol = this.render(cl.primary(), entry.indent(), entry.mode(), col, probe, probeSuffixes);
            for (final StackEntry suffix : probeSuffixes) {
              this.render(suffix.document(), suffix.indent(), suffix.mode(), 0, probe, new ArrayList<>());
            }
            if (this.allLinesWithin(probe, col)) {
              output.append(probe);
              col = endCol;
            } else {
              groupBroken.clear();
              groupBroken.putAll(savedGroupState);
              stack.push(new StackEntry(entry.indent(), entry.mode(), cl.fallback()));
            }
          } else {
            final Document chosen = this.fits(printWidth - col, new StackEntry(entry.indent(), Mode.FLAT, cl.primary()), stack, true)
              ? cl.primary()
              : cl.fallback();
            stack.push(new StackEntry(entry.indent(), entry.mode(), chosen));
          }
        }
      }
    }
    return col;
  }

  /**
   * Check if a document fits within the remaining line width when printed in FLAT mode. Once {@code entry}'s contents are exhausted, the
   * simulation continues into {@code restStack} (the caller's outer stack at the point of the call) so trailing content past the group's
   * close — e.g. the {@code ;} after a method call's argument list — counts toward the budget. Without that, a flat group can fit by 1 char
   * while the surrounding line still overflows.
   */
  private boolean fits(final int remaining, final StackEntry entry, final ArrayDeque<StackEntry> restStack) {
    return this.fits(remaining, entry, restStack, false);
  }

  /**
   * Core {@code fits} simulation (see the one-argument overload for the rest-stack contract). Per-variant notes:
   * <ul>
   *   <li><b>Text:</b> a multi-line text behaves like its first line followed by a forced break — consume
   *       the first line's width, then the embedded newline ends the measured line exactly like a HardLine,
   *       fitting in BREAK mode (or under {@code hardLineFits}) and failing in FLAT.</li>
   *   <li><b>HardLine:</b> in BREAK mode the line is already broken so the prefix fits trivially; in FLAT
   *       mode the forced newline means the contents can't render on one line (signalling the enclosing
   *       group to break) unless {@code hardLineFits}, where it is an acceptable end of the measured line.</li>
   *   <li><b>Group:</b> inherits the entry's mode rather than forcing FLAT. When fits drains its rest stack
   *       (entries carrying BREAK from the calling render's outer break), nested groups must propagate that
   *       BREAK so any Line/SoftLine/HardLine inside them registers as a break opportunity; forcing FLAT here
   *       would eager-break earlier groups because fits could never see a break point in the rest of the
   *       line.</li>
   *   <li><b>IndentIfBreak:</b> indent doesn't affect the character budget (only Line/HardLine short-circuit
   *       on mode), so contents pass through unchanged.</li>
   *   <li><b>ConditionalLayout:</b> like prettier, fits treats it as a group with expanded states — BREAK
   *       mode measures the last/most-expanded state (fallback), FLAT mode the first (primary). The expanded
   *       chain/argument list begins with an early break, so an outer group probing this in BREAK mode (e.g.
   *       an assignment's break-after-{@code =} group measuring its RHS) stops at that break and reports a
   *       fit, keeping {@code id = call(} on one line while the args break. Strictness only changes how the
   *       layout renders, not how an enclosing line measures past it.</li>
   * </ul>
   *
   * @param hardLineFits when {@code true}, a {@link HardLine} reached in FLAT mode counts as a successful line end (the prefix up to it
   *                     fits) rather than a failure. This is prettier's {@code fits} semantics used by {@code conditionalGroup}: an option
   *                     "fits" if its first line fits, so a forced break (e.g. a block lambda's body) terminates the measured line instead
   *                     of disqualifying the option. The default ({@code false}) keeps jacet's group-break-propagation behavior where a
   *                     FLAT-mode hard line forces the enclosing group open.
   */
  private boolean fits(final int remaining, final StackEntry entry, final ArrayDeque<StackEntry> restStack, final boolean hardLineFits) {
    final ArrayDeque<StackEntry> fitStack = new ArrayDeque<>();
    fitStack.push(entry);
    final Iterator<StackEntry> restIterator = restStack.iterator();
    int rem = remaining;

    while (rem >= 0) {
      final StackEntry current;
      if (!fitStack.isEmpty()) {
        current = fitStack.pop();
      } else if (restIterator.hasNext()) {
        current = restIterator.next();
      } else {
        break;
      }

      switch (current.document()) {
        case final Text t -> {
          final int firstBreak = firstLineBreak(t.value());
          if (firstBreak < 0) {
            rem -= t.value().length();
          } else {
            rem -= firstBreak;
            if (rem < 0) {
              return false;
            }
            return current.mode() != Mode.FLAT || hardLineFits;
          }
        }
        case final Concat c -> {
          final List<Document> parts = c.parts();
          for (int i = parts.size() - 1; i >= 0; i--) {
            fitStack.push(new StackEntry(current.indent(), current.mode(), parts.get(i)));
          }
        }
        case final Line _ -> {
          if (current.mode() == Mode.FLAT) {
            rem -= 1;
          } else {
            return true;
          }
        }
        case final SoftLine _ -> {
          if (current.mode() != Mode.FLAT) {
            return true;
          }
        }
        case final HardLine _ -> {
          return current.mode() != Mode.FLAT || hardLineFits;
        }
        case final Group g -> {
          final Mode groupMode = g.shouldBreak() ? Mode.BREAK : current.mode();
          fitStack.push(new StackEntry(current.indent(), groupMode, g.contents()));
        }
        case final Indent i -> fitStack.push(new StackEntry(current.indent() + tabWidth, current.mode(), i.contents()));
        case final BreakIndent bi -> {
          final int newIndent = current.mode() == Mode.BREAK ? current.indent() + tabWidth : current.indent();
          fitStack.push(new StackEntry(newIndent, current.mode(), bi.contents()));
        }
        case final IndentIfBreak iib -> {
          fitStack.push(new StackEntry(current.indent(), current.mode(), iib.contents()));
        }
        case final IfBreak ib -> {
          if (current.mode() == Mode.BREAK) {
            fitStack.push(new StackEntry(current.indent(), current.mode(), ib.breakContents()));
          } else {
            fitStack.push(new StackEntry(current.indent(), current.mode(), ib.flatContents()));
          }
        }
        case final LineSuffix _ -> {}
        case final ConditionalLayout cl -> {
          final Document chosen = current.mode() == Mode.BREAK ? cl.fallback() : cl.primary();
          fitStack.push(new StackEntry(current.indent(), current.mode(), chosen));
        }
      }
    }

    return rem >= 0;
  }

  /** Index of the first {@code \n} or {@code \r} in {@code value}, or -1 if the text is single-line. */
  private static int firstLineBreak(final String value) {
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (c == '\n' || c == '\r') {
        return i;
      }
    }
    return -1;
  }

  /** Index of the last {@code \n} or {@code \r} in {@code value}, or -1 if the text is single-line. */
  private static int lastLineBreak(final String value) {
    for (int i = value.length() - 1; i >= 0; i--) {
      final char c = value.charAt(i);
      if (c == '\n' || c == '\r') {
        return i;
      }
    }
    return -1;
  }

  private boolean allLinesWithin(final CharSequence rendered, final int startCol) {
    int col = startCol;
    final int eolLen = endOfLine.length();
    for (int i = 0; i < rendered.length(); i++) {
      if (i + eolLen <= rendered.length() && this.eolStartsAt(rendered, i)) {
        i += eolLen - 1;
        col = 0;
      } else {
        col++;
        if (col > printWidth) {
          return false;
        }
      }
    }
    return true;
  }

  /** Allocation-free check that {@code rendered} carries the configured line ending at index {@code i}. */
  private boolean eolStartsAt(final CharSequence rendered, final int i) {
    for (int j = 0; j < endOfLine.length(); j++) {
      if (rendered.charAt(i + j) != endOfLine.charAt(j)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Flush all queued line suffixes to output, then emit newline + indent. Returns the new column position.
   */
  private int flushLineSuffixesAndNewline(final List<StackEntry> lineSuffixes, final StringBuilder output, final int indent) {
    for (final StackEntry suffix : lineSuffixes) {
      this.render(suffix.document(), suffix.indent(), suffix.mode(), 0, output, new ArrayList<>());
    }
    lineSuffixes.clear();
    this.appendNewlineWithIndent(output, indent);
    return indent;
  }

  /**
   * Emits a newline plus indent, first stripping trailing spaces/tabs off the current line. Without the strip, two consecutive HardLines at
   * indent N would leave the line between the newlines as whitespace-only — a trailing-whitespace bug.
   */
  private void appendNewlineWithIndent(final StringBuilder output, final int indent) {
    int end = output.length();
    while (end > 0) {
      final char c = output.charAt(end - 1);
      if (c != ' ' && c != '\t') {
        break;
      }
      end--;
    }
    output.setLength(end);
    output.append(endOfLine);
    output.append(this.indentString(indent));
  }

  private String indentString(final int indent) {
    if (indent <= 0) {
      return "";
    }
    return indentCache.computeIfAbsent(indent, this::buildIndent);
  }

  private String buildIndent(final int indent) {
    if (useTabs) {
      final int tabs = indent / tabWidth;
      final int spaces = indent % tabWidth;
      return "\t".repeat(tabs) + " ".repeat(spaces);
    }
    return " ".repeat(indent);
  }

  private enum Mode {
    FLAT,
    BREAK,
  }

  private record StackEntry(int indent, Mode mode, Document document) {}
}
