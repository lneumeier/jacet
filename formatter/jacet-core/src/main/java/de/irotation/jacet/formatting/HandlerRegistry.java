package de.irotation.jacet.formatting;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;

import de.irotation.jacet.document.Document;

final class HandlerRegistry {
  private final Map<Class<? extends ParserRuleContext>, Function<ParserRuleContext, Document>> handlers = new IdentityHashMap<>();

  @SuppressWarnings("unchecked")
  <T extends ParserRuleContext> void register(final Class<T> contextType, final Function<T, Document> handler) {
    final Function<ParserRuleContext, Document> existing = handlers.putIfAbsent(
      contextType,
      (Function<ParserRuleContext, Document>) handler
    );
    if (existing != null) {
      throw new IllegalStateException("Duplicate handler registration for " + contextType.getSimpleName());
    }
  }

  @Nullable
  Function<ParserRuleContext, Document> get(final Class<? extends ParserRuleContext> contextType) {
    return handlers.get(contextType);
  }
}
