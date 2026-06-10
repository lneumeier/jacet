package de.irotation.jacet.formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comments attached to a parser-rule node by {@link CommentAttacher}.
 *
 * <p>{@code leading} — comments rendered before the node.
 * {@code trailing} — comments rendered after the node (same-line, as {@code LineSuffix}).
 * {@code dangling} — comments inside the node that don't attach to any specific child (e.g. inside an empty body).
 */
record NodeComments(List<Comment> leading, List<Comment> trailing, List<Comment> dangling) {

  static final NodeComments EMPTY = new NodeComments(List.of(), List.of(), List.of());

  static Mutable mutable() {
    return new Mutable();
  }

  /** Mutable builder used during attachment. */
  static final class Mutable {
    private final List<Comment> leading = new ArrayList<>();
    private final List<Comment> trailing = new ArrayList<>();
    private final List<Comment> dangling = new ArrayList<>();

    void addLeading(final Comment comment) {
      this.leading.add(comment);
    }

    void addTrailing(final Comment comment) {
      this.trailing.add(comment);
    }

    void addDangling(final Comment comment) {
      this.dangling.add(comment);
    }

    NodeComments freeze() {
      return new NodeComments(
        Collections.unmodifiableList(this.leading),
        Collections.unmodifiableList(this.trailing),
        Collections.unmodifiableList(this.dangling)
      );
    }
  }
}
