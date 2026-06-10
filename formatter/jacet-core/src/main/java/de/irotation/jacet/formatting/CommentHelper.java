package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.empty;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.lineSuffix;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaLexer;

/**
 * Renders hidden-channel comments. Provides token-index-based queries (legacy API used by visitors during migration) and node-attachment
 * rendering (new API backed by {@link CommentAttacher}). A shared printed-set ensures the same comment is never emitted twice when both
 * APIs are in flight.
 */
public final class CommentHelper {
  private final CommonTokenStream tokens;
  private final Set<Integer> printedIndices = new HashSet<>();

  public CommentHelper(final CommonTokenStream tokens) {
    this.tokens = tokens;
  }

  /**
   * Read-only view of the token indices of every comment emitted so far. Populated during the visitor run by
   * the {@code render*}/{@code trailing} methods; complete once {@code DocumentVisitor.visit} returns. Backs
   * the Prettier-style {@code ensureAllCommentsPrinted} check in {@code CoverageVerifier}.
   */
  public Set<Integer> printedCommentIndices() {
    return Collections.unmodifiableSet(printedIndices);
  }

  public List<Document> leadingComments(final int tokenIndex) {
    final List<Token> hidden = tokens.getHiddenTokensToLeft(tokenIndex);
    if (hidden == null) {
      return List.of();
    }

    final List<Document> documents = new ArrayList<>();
    for (final Token token : hidden) {
      if (printedIndices.contains(token.getTokenIndex())) {
        continue;
      }
      final String text = token.getText().trim();
      if (text.isEmpty()) {
        continue;
      }
      if (this.isTrailingComment(token)) {
        continue;
      }
      if (text.startsWith("//")) {
        documents.add(text(text));
        documents.add(hardLine());
        printedIndices.add(token.getTokenIndex());
      } else if (text.startsWith("/*")) {
        documents.add(formatBlockComment(text, token.getCharPositionInLine()));
        documents.add(hardLine());
        printedIndices.add(token.getTokenIndex());
      }
    }
    return documents;
  }

  public boolean hasBlankLineBefore(final ParserRuleContext context) {
    if (context.getStart() == null) {
      return false;
    }
    return this.hasBlankLineBefore(context.getStart().getTokenIndex());
  }

  /**
   * Reports whether the visitor should emit a blank-line separator before the node at {@code tokenIndex}.
   * Walks the hidden tokens left-to-right; which blank-line whitespace counts depends on the comments
   * already passed:
   *
   * <pre>
   *   prev;            &larr; case A: blank before any comments — the visitor emits it
   *
   *   // c
   *   stmt;
   *
   *   prev; // trail   &larr; case D: blank after a same-line trailing comment of the *previous* stmt —
   *   stmt;               still emitted; the trailing comment belongs to prev, not to stmt
   *
   *   prev;            &larr; case B: blank between leading comments belongs to the next comment's
   *   // c1               precedingBlankLine — not emitted here
   *   // c2
   *   stmt;
   *
   *   prev;            &larr; case C: blank between the leading comments and the stmt body belongs to the
   *   // c                last comment's trailingBlankLine — not emitted here
   *   stmt;
   * </pre>
   */
  public boolean hasBlankLineBefore(final int tokenIndex) {
    final List<Token> hidden = tokens.getHiddenTokensToLeft(tokenIndex);
    if (hidden == null) {
      return false;
    }
    boolean prevWsHadNewline = true;
    boolean sawLeadingComment = false;
    for (final Token token : hidden) {
      if (token.getType() == JavaLexer.LINE_COMMENT || token.getType() == JavaLexer.COMMENT) {
        if (prevWsHadNewline) {
          sawLeadingComment = true;
        }
        continue;
      }
      if (token.getType() != JavaLexer.WS) {
        continue;
      }
      final String text = token.getText();
      int newlines = 0;
      for (int j = 0; j < text.length(); j++) {
        if (text.charAt(j) == '\n') {
          newlines++;
        }
      }
      if (newlines >= 2 && !sawLeadingComment) {
        return true;
      }
      prevWsHadNewline = newlines >= 1;
    }
    return false;
  }

  private boolean isTrailingComment(final Token commentToken) {
    final int index = commentToken.getTokenIndex();
    for (int i = index - 1; i >= 0; i--) {
      final Token previous = tokens.get(i);
      if (previous.getLine() != commentToken.getLine()) {
        return false;
      }
      if (previous.getChannel() == Token.DEFAULT_CHANNEL) {
        return true;
      }
    }
    return false;
  }

  public Document trailingComment(final int tokenIndex) {
    final List<Token> hidden = tokens.getHiddenTokensToRight(tokenIndex);
    if (hidden == null) {
      return empty();
    }
    for (final Token token : hidden) {
      if (printedIndices.contains(token.getTokenIndex())) {
        continue;
      }
      final String raw = token.getText();
      if (raw.contains("\n") || raw.contains("\r")) {
        break;
      }
      final String text = raw.trim();
      if (text.startsWith("//")) {
        printedIndices.add(token.getTokenIndex());
        return lineSuffix(concat(text(" "), text(text)));
      }
    }
    return empty();
  }

  /**
   * Render leading comments attached to a node by {@link CommentAttacher}. Each rendered comment is marked printed so the legacy
   * token-index API skips it. Blank lines between comments in the source are preserved by inserting an extra {@code hardLine} before any
   * non-first comment whose {@code precedingBlankLine} flag is set. After the last comment, a source blank
   * line between it and the statement is emitted here (via the comment's {@code trailingBlankLine} flag) so
   * the gap stays <em>after</em> the comments rather than being pulled up before them by
   * {@link #hasBlankLineBefore}.
   */
  public List<Document> renderLeading(final NodeComments nodeComments) {
    final List<Document> documents = new ArrayList<>();
    boolean first = true;
    Comment lastEmitted = null;
    for (final Comment comment : nodeComments.leading()) {
      if (printedIndices.contains(comment.tokenIndex())) {
        continue;
      }
      if (!first && comment.precedingBlankLine()) {
        documents.add(hardLine());
      }
      documents.add(renderCommentBody(comment));
      documents.add(hardLine());
      printedIndices.add(comment.tokenIndex());
      first = false;
      lastEmitted = comment;
    }
    if (lastEmitted != null && lastEmitted.trailingBlankLine()) {
      documents.add(hardLine());
    }
    return documents;
  }

  /**
   * Render trailing comments attached to a node by {@link CommentAttacher}. Same-line line-comments are emitted as {@code LineSuffix} so
   * they queue until the next newline; block comments are emitted inline.
   */
  public Document renderTrailing(final NodeComments nodeComments) {
    final List<Document> parts = new ArrayList<>();
    for (final Comment comment : nodeComments.trailing()) {
      if (printedIndices.contains(comment.tokenIndex())) {
        continue;
      }
      if (comment.isLineComment()) {
        parts.add(lineSuffix(concat(text(" "), text(comment.text().trim()))));
      } else {
        parts.add(text(" "));
        parts.add(renderCommentBody(comment));
      }
      printedIndices.add(comment.tokenIndex());
    }
    if (parts.isEmpty()) {
      return empty();
    }
    return concat(parts);
  }

  /**
   * Render dangling comments (attached to an enclosing node when no preceding/following sibling exists). Comments are separated by
   * {@code hardLine}; a {@code precedingBlankLine} flag inserts an extra hardLine before that comment, mirroring the source layout. There
   * is no trailing hardLine — the body renderer is responsible for the newline before {@code }}.
   */
  public List<Document> renderDangling(final NodeComments nodeComments) {
    final List<Document> documents = new ArrayList<>();
    for (final Comment comment : nodeComments.dangling()) {
      if (printedIndices.contains(comment.tokenIndex())) {
        continue;
      }
      if (!documents.isEmpty()) {
        documents.add(hardLine());
      }
      if (comment.precedingBlankLine()) {
        documents.add(hardLine());
      }
      documents.add(renderCommentBody(comment));
      printedIndices.add(comment.tokenIndex());
    }
    return documents;
  }

  private static Document renderCommentBody(final Comment comment) {
    final String text = comment.text().trim();
    if (comment.isBlockComment()) {
      return formatBlockComment(text, comment.token().getCharPositionInLine());
    }
    return text(text);
  }

  /**
   * Renders a block comment. An <em>indentable</em> block comment — one whose every continuation line,
   * trimmed, starts with an asterisk — is reindented prettier-style (mirroring {@code
   * printIndentableBlockComment}): each continuation is trimmed and re-prefixed with a single space so its
   * leading asterisk aligns under the opening delimiter regardless of the comment's original source column.
   * Column-based stripping is unreliable for trailing comments (e.g. a comment opened after {@code }} on the
   * same line) where the opening-delimiter column exceeds the continuation indent the author actually used.
   */
  static Document formatBlockComment(final String comment, final int startColumn) {
    final String[] lines = comment.split("\\R");
    if (lines.length == 1) {
      return text(comment);
    }
    final boolean indentable = isIndentableBlockComment(lines);
    final List<Document> parts = new ArrayList<>();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        parts.add(hardLine());
      }
      final String raw = lines[i];
      final String rendered;
      if (i == 0) {
        rendered = raw.stripTrailing();
      } else if (indentable) {
        rendered = (" " + raw.strip()).stripTrailing();
      } else {
        rendered = stripLeadingUpTo(raw, startColumn).stripTrailing();
      }
      parts.add(text(rendered));
    }
    return concat(parts);
  }

  private static boolean isIndentableBlockComment(final String[] lines) {
    for (int i = 1; i < lines.length; i++) {
      if (!lines[i].strip().startsWith("*")) {
        return false;
      }
    }
    return true;
  }

  private static String stripLeadingUpTo(final String s, final int maxStrip) {
    int i = 0;
    while (i < maxStrip && i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
      i++;
    }
    return s.substring(i);
  }
}
