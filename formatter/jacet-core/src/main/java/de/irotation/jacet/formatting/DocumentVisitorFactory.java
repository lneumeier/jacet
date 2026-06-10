package de.irotation.jacet.formatting;

import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Creates a fully wired {@link DocumentVisitor}. Registers all delegate formatting handlers into a {@link HandlerRegistry} and resolves the
 * circular dependency between visitor and delegates via a {@link FormattingDispatchHolder}.
 */
public final class DocumentVisitorFactory {

  private DocumentVisitorFactory() {}

  public static DocumentVisitor create(
    final CommonTokenStream tokens,
    final ParserRuleContext root,
    final boolean forceBraces,
    final int printWidth
  ) {
    final HandlerRegistry registry = new HandlerRegistry();
    final FormattingDispatchHolder dispatchHolder = new FormattingDispatchHolder();

    final ControlFlowFormatter controlFlow = new ControlFlowFormatter(dispatchHolder, forceBraces);
    final SwitchFormatter switchFormatter = new SwitchFormatter(dispatchHolder);
    final ExceptionFormatter exception = new ExceptionFormatter(dispatchHolder);

    final List<HandlerProvider> formatters = List.of(
      new CompilationUnitFormatter(dispatchHolder),
      new DeclarationFormatter(dispatchHolder),
      new ClassBodyFormatter(dispatchHolder),
      new MethodFormatter(dispatchHolder),
      new StatementFormatter(dispatchHolder, controlFlow, switchFormatter, exception),
      controlFlow,
      switchFormatter,
      exception,
      new OperatorFormatter(dispatchHolder),
      new InvocationFormatter(dispatchHolder, printWidth),
      new CreatorFormatter(dispatchHolder),
      new LambdaFormatter(dispatchHolder),
      new TypeFormatter(dispatchHolder),
      new LeafFormatter(dispatchHolder),
      new ModuleFormatter(dispatchHolder)
    );

    for (final HandlerProvider formatter : formatters) {
      formatter.registerHandlers(registry);
    }

    final Map<ParserRuleContext, NodeComments> attachment = new CommentAttacher(tokens).attach(root);
    final DocumentVisitor visitor = new DocumentVisitor(tokens, registry, attachment);
    dispatchHolder.initialize(visitor);
    return visitor;
  }
}
