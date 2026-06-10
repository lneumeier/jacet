package de.irotation.jacet.formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import de.irotation.jacet.parser.JavaLexer;

/**
 * Pre-pass that walks all hidden-channel comments and attaches each to the closest semantic AST node as leading, trailing, or dangling.
 *
 * <p>Algorithm per comment:
 * <ol>
 *   <li>Find the deepest enclosing parser-rule node whose token range covers the comment's index.</li>
 *   <li>Find the rightmost child rule whose stop is left of the comment ({@code precedingNode}) and the leftmost child rule whose start is
 *       right of the comment ({@code followingNode}).</li>
 *   <li>If both exist: same-line as preceding → trailing of preceding; otherwise leading of following.</li>
 *   <li>If only one exists: leading of following or trailing of preceding accordingly.</li>
 *   <li>If neither exists: dangling on the enclosing node.</li>
 * </ol>
 */
final class CommentAttacher {

  private final CommonTokenStream tokens;

  CommentAttacher(final CommonTokenStream tokens) {
    this.tokens = tokens;
  }

  /**
   * Attaches every hidden-channel comment to a node as leading, trailing, or dangling.
   *
   * <p>Two edge cases drive placement:
   * <ul>
   *   <li><b>File-boundary comments:</b> {@code findEnclosing} returns null for a comment outside the root
   *       rule's token span — a file header before the first real token, or a comment before EOF — since
   *       those hidden tokens fall outside {@code [root.start, root.stop]}. Such comments fall back to the
   *       root so they still attach (leading of the first declaration / trailing of the last) rather than
   *       being silently dropped.</li>
   *   <li><b>End-of-body comments:</b> a comment with only a preceding sibling becomes that sibling's
   *       trailing comment only when on the same source line. Otherwise a stand-alone comment before a
   *       closing brace would glue to the last sibling, swallowing its blank line; falling through to
   *       dangling on the enclosing rule lets the body renderer place it at body indent before the brace.</li>
   * </ul>
   */
  Map<ParserRuleContext, NodeComments> attach(final ParserRuleContext root) {
    final List<Comment> comments = this.collectComments();
    final Map<ParserRuleContext, NodeComments.Mutable> mutableMap = new IdentityHashMap<>();

    for (final Comment comment : comments) {
      ParserRuleContext enclosing = this.findEnclosing(root, comment.tokenIndex());
      if (enclosing == null) {
        enclosing = root;
      }
      final ParserRuleContext preceding = findPrecedingChildRule(enclosing, comment.tokenIndex());
      final ParserRuleContext following = findFollowingChildRule(enclosing, comment.tokenIndex());

      if (preceding != null && following != null) {
        if (sameLineAsPrecedingStop(comment, preceding)) {
          mutableFor(mutableMap, preceding).addTrailing(comment);
        } else {
          mutableFor(mutableMap, following).addLeading(comment);
        }
      } else if (following != null) {
        mutableFor(mutableMap, following).addLeading(comment);
      } else if (preceding != null) {
        if (sameLineAsPrecedingStop(comment, preceding)) {
          mutableFor(mutableMap, preceding).addTrailing(comment);
        } else {
          mutableFor(mutableMap, enclosing).addDangling(comment);
        }
      } else {
        mutableFor(mutableMap, enclosing).addDangling(comment);
      }
    }

    final Map<ParserRuleContext, NodeComments> frozen = new IdentityHashMap<>();
    for (final Map.Entry<ParserRuleContext, NodeComments.Mutable> entry : mutableMap.entrySet()) {
      frozen.put(entry.getKey(), entry.getValue().freeze());
    }
    return Collections.unmodifiableMap(frozen);
  }

  private List<Comment> collectComments() {
    tokens.fill();
    final List<Token> all = tokens.getTokens();
    final List<Comment> result = new ArrayList<>();
    for (int i = 0; i < all.size(); i++) {
      final Token token = all.get(i);
      if (!isCommentToken(token)) {
        continue;
      }
      final boolean precedingNewline = hasNewlineBefore(all, i);
      final boolean trailingNewline = hasNewlineAfter(all, i);
      final boolean precedingBlankLine = hasBlankLineBefore(all, i);
      final boolean trailingBlankLine = hasBlankLineAfter(all, i);
      result.add(new Comment(token, precedingNewline, trailingNewline, precedingBlankLine, trailingBlankLine));
    }
    return result;
  }

  private static boolean isCommentToken(final Token token) {
    final int type = token.getType();
    return type == JavaLexer.LINE_COMMENT || type == JavaLexer.COMMENT;
  }

  /**
   * Counts newlines in the WS token directly before this comment (skipping non-comment hidden tokens). ≥2 means there's a blank line
   * between this comment and whatever came before it.
   */
  private static boolean hasBlankLineBefore(final List<Token> all, final int index) {
    for (int i = index - 1; i >= 0; i--) {
      final Token previous = all.get(i);
      if (previous.getChannel() == Token.DEFAULT_CHANNEL) {
        return false;
      }
      if (previous.getType() == JavaLexer.WS) {
        int newlines = 0;
        final String text = previous.getText();
        for (int j = 0; j < text.length(); j++) {
          if (text.charAt(j) == '\n') {
            newlines++;
            if (newlines >= 2) {
              return true;
            }
          }
        }
        return false;
      }
      if (isCommentToken(previous)) {
        return false;
      }
    }
    return false;
  }

  /**
   * Counts newlines in the WS token directly after this comment (skipping further comments). ≥2 means there's a blank line between this
   * comment and whatever follows it.
   */
  private static boolean hasBlankLineAfter(final List<Token> all, final int index) {
    for (int i = index + 1; i < all.size(); i++) {
      final Token next = all.get(i);
      if (next.getChannel() == Token.DEFAULT_CHANNEL) {
        return false;
      }
      if (next.getType() == JavaLexer.WS) {
        int newlines = 0;
        final String text = next.getText();
        for (int j = 0; j < text.length(); j++) {
          if (text.charAt(j) == '\n') {
            newlines++;
            if (newlines >= 2) {
              return true;
            }
          }
        }
        return false;
      }
      if (isCommentToken(next)) {
        return false;
      }
    }
    return false;
  }

  private static boolean hasNewlineBefore(final List<Token> all, final int index) {
    for (int i = index - 1; i >= 0; i--) {
      final Token previous = all.get(i);
      if (previous.getChannel() == Token.DEFAULT_CHANNEL) {
        return false;
      }
      if (previous.getType() == JavaLexer.WS && containsNewline(previous.getText())) {
        return true;
      }
      if (isCommentToken(previous)) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasNewlineAfter(final List<Token> all, final int index) {
    final Token comment = all.get(index);
    if (comment.getType() == JavaLexer.LINE_COMMENT) {
      return true;
    }
    for (int i = index + 1; i < all.size(); i++) {
      final Token next = all.get(i);
      if (next.getChannel() == Token.DEFAULT_CHANNEL) {
        return false;
      }
      if (next.getType() == JavaLexer.WS && containsNewline(next.getText())) {
        return true;
      }
      if (isCommentToken(next)) {
        return false;
      }
    }
    return true;
  }

  private static boolean containsNewline(final String text) {
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == '\n' || c == '\r') {
        return true;
      }
    }
    return false;
  }

  private @Nullable ParserRuleContext findEnclosing(final ParserRuleContext node, final int tokenIdx) {
    if (node.getStart() == null || node.getStop() == null) {
      return null;
    }
    if (tokenIdx < node.getStart().getTokenIndex() || tokenIdx > node.getStop().getTokenIndex()) {
      return null;
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      if (node.getChild(i) instanceof final ParserRuleContext child) {
        final ParserRuleContext deeper = this.findEnclosing(child, tokenIdx);
        if (deeper != null) {
          return deeper;
        }
      }
    }
    return node;
  }

  private static @Nullable ParserRuleContext findPrecedingChildRule(final ParserRuleContext parent, final int commentIdx) {
    ParserRuleContext result = null;
    for (int i = 0; i < parent.getChildCount(); i++) {
      if (parent.getChild(i) instanceof final ParserRuleContext child) {
        if (child.getStop() == null) {
          continue;
        }
        if (child.getStop().getTokenIndex() < commentIdx) {
          result = child;
        } else {
          break;
        }
      }
    }
    return result;
  }

  private static @Nullable ParserRuleContext findFollowingChildRule(final ParserRuleContext parent, final int commentIdx) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      if (parent.getChild(i) instanceof final ParserRuleContext child) {
        if (child.getStart() == null) {
          continue;
        }
        if (child.getStart().getTokenIndex() > commentIdx) {
          return child;
        }
      }
    }
    return null;
  }

  private static boolean sameLineAsPrecedingStop(final Comment comment, final ParserRuleContext preceding) {
    if (preceding.getStop() == null) {
      return false;
    }
    return comment.token().getLine() == preceding.getStop().getLine();
  }

  private static NodeComments.Mutable mutableFor(final Map<ParserRuleContext, NodeComments.Mutable> map, final ParserRuleContext node) {
    return map.computeIfAbsent(node, ignored -> NodeComments.mutable());
  }
}
