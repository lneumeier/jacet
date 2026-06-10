package de.irotation.jacet.document;

import java.util.List;

/** Sequence of documents, printed one after another. */
public record Concat(List<Document> parts) implements Document {
  public Concat {
    parts = List.copyOf(parts);
  }
}
