package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats switch statements, switch labels, and switch rules.
 */
final class SwitchFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  SwitchFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.SwitchBlockStatementGroupContext.class, this::visitSwitchBlockStatementGroup);
    registry.register(JavaParser.SwitchLabelContext.class, this::visitSwitchLabel);
    registry.register(JavaParser.SwitchLabeledRuleContext.class, this::visitSwitchLabeledRule);
    registry.register(JavaParser.SwitchRuleOutcomeContext.class, this::visitSwitchRuleOutcome);
    registry.register(JavaParser.SwitchExpressionContext.class, this::visitSwitchExpression);
    registry.register(JavaParser.ExpressionSwitchContext.class, this::visitExpressionSwitch);
  }

  Document visitExpressionSwitch(final JavaParser.ExpressionSwitchContext expressionContext) {
    return dispatch.visit(expressionContext.switchExpression());
  }

  /**
   * Formats a switch expression ({@code switch (x) { case ... -> ...; }}). Dangling stand-alone comments past the last arm are rendered at
   * body indent before the closing brace.
   */
  Document visitSwitchExpression(final JavaParser.SwitchExpressionContext switchContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(switchContext.SWITCH()));
    parts.add(text(" "));
    parts.add(Tokens.sourced(switchContext.LPAREN()));
    parts.add(dispatch.visit(switchContext.expression()));
    parts.add(Tokens.sourced(switchContext.RPAREN()));
    parts.add(text(" "));
    parts.add(Tokens.sourced(switchContext.LBRACE()));

    for (final JavaParser.SwitchLabeledRuleContext rule : switchContext.switchLabeledRule()) {
      parts.add(indent(concat(hardLine(), dispatch.visit(rule))));
    }

    final List<Document> dangling = dispatch.dangling(switchContext);
    if (!dangling.isEmpty()) {
      parts.add(indent(concat(hardLine(), concat(dangling))));
    }

    parts.add(hardLine());
    parts.add(Tokens.sourced(switchContext.RBRACE()));
    return concat(parts);
  }

  /**
   * Formats a {@code switch} statement. Dangling stand-alone comments past the last group/label are rendered at body indent before the
   * closing brace.
   */
  Document visitSwitchStatement(final JavaParser.StatementContext statementContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(statementContext.SWITCH()));
    parts.add(text(" "));
    parts.add(Tokens.sourced(statementContext.LPAREN()));
    parts.add(dispatch.visit(statementContext.expression(0)));
    parts.add(Tokens.sourced(statementContext.RPAREN()));
    parts.add(text(" "));
    parts.add(Tokens.sourced(statementContext.LBRACE()));

    final List<Document> bodyParts = new ArrayList<>();
    for (final JavaParser.SwitchBlockStatementGroupContext statementGroup : statementContext.switchBlockStatementGroup()) {
      bodyParts.add(hardLine());
      bodyParts.add(dispatch.visit(statementGroup));
    }
    for (final JavaParser.SwitchLabelContext label : statementContext.switchLabel()) {
      bodyParts.add(hardLine());
      bodyParts.add(dispatch.visit(label));
      bodyParts.add(text(":"));
    }
    final List<Document> dangling = dispatch.dangling(statementContext);
    if (!dangling.isEmpty()) {
      bodyParts.add(hardLine());
      bodyParts.addAll(dangling);
    }

    parts.add(indent(concat(bodyParts)));
    parts.add(hardLine());
    parts.add(Tokens.sourced(statementContext.RBRACE()));
    return concat(parts);
  }

  /**
   * Formats a colon-style switch group (labels plus statements). A lone block statement is hugged to the colon ({@code case X: { ... }},
   * the IntelliJ/Spotless convention); otherwise the leading hardLine would put the block's own
   * {@code {} on its own line, indented one step below the case label.
   */
  Document visitSwitchBlockStatementGroup(final JavaParser.SwitchBlockStatementGroupContext groupContext) {
    final List<Document> parts = new ArrayList<>();
    final List<JavaParser.SwitchLabelContext> labels = groupContext.switchLabel();
    final List<TerminalNode> colons = groupContext.COLON();
    for (int i = 0; i < labels.size(); i++) {
      parts.add(dispatch.visit(labels.get(i)));
      parts.add(Tokens.sourced(colons.get(i)));
      parts.add(dispatch.trailingComment(colons.get(i).getSymbol().getTokenIndex()));
      if (i + 1 < labels.size()) {
        parts.add(hardLine());
      }
    }
    final List<JavaParser.BlockStatementContext> blockStmts = groupContext.blockStatement();
    if (blockStmts.size() == 1 && isSingleBlockStatement(blockStmts.getFirst())) {
      parts.add(text(" "));
      parts.add(dispatch.visit(blockStmts.getFirst()));
      return concat(parts);
    }
    final List<Document> bodyParts = new ArrayList<>();
    for (final JavaParser.BlockStatementContext blockStmt : blockStmts) {
      bodyParts.add(hardLine());
      bodyParts.add(dispatch.visit(blockStmt));
    }
    parts.add(indent(concat(bodyParts)));
    return concat(parts);
  }

  private static boolean isSingleBlockStatement(final JavaParser.BlockStatementContext bs) {
    final JavaParser.StatementContext stmt = bs.statement();
    return stmt != null && stmt.block() != null;
  }

  /**
   * Formats a switch label without its trailing colon — the colon belongs to the enclosing {@code switchBlockStatementGroup} (grammar:
   * {@code (switchLabel ':')+}) and is sourced by the caller.
   */
  Document visitSwitchLabel(final JavaParser.SwitchLabelContext labelContext) {
    if (labelContext.CASE() == null) {
      return Tokens.sourced(labelContext.DEFAULT());
    }
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(labelContext.CASE()));
    parts.add(text(" "));
    final List<Document> caseItems = new ArrayList<>();
    final List<TerminalNode> separators;
    if (labelContext.expressionList() != null) {
      labelContext.expressionList().expression().stream().map(dispatch::visit).forEach(caseItems::add);
      separators = labelContext.expressionList().COMMA();
    } else if (labelContext.NULL_LITERAL() != null) {
      caseItems.add(Tokens.sourced(labelContext.NULL_LITERAL()));
      if (labelContext.DEFAULT() != null) {
        caseItems.add(Tokens.sourced(labelContext.DEFAULT()));
      }
      separators = labelContext.COMMA();
    } else {
      for (final JavaParser.CasePatternContext cp : labelContext.casePattern()) {
        caseItems.add(dispatch.visit(cp));
      }
      separators = labelContext.COMMA();
    }
    parts.add(Tokens.joinSourced(caseItems, separators, text(" ")));
    if (labelContext.guard() != null) {
      parts.add(text(" "));
      parts.add(dispatch.visit(labelContext.guard()));
    }
    return concat(parts);
  }

  /**
   * Formats an arrow-style switch rule. A multi-value case prints flat as {@code case A, B, C} and, when it doesn't fit, breaks to
   * {@code case} with each value on its own indented line.
   */
  Document visitSwitchLabeledRule(final JavaParser.SwitchLabeledRuleContext labeledRuleContext) {
    final List<Document> parts = new ArrayList<>(dispatch.leading(labeledRuleContext));
    if (labeledRuleContext.DEFAULT() != null) {
      parts.add(Tokens.sourced(labeledRuleContext.DEFAULT()));
    } else {
      final List<Document> caseItems = new ArrayList<>();
      final List<TerminalNode> separators;
      if (labeledRuleContext.expressionList() != null) {
        labeledRuleContext.expressionList().expression().stream().map(dispatch::visit).forEach(caseItems::add);
        separators = labeledRuleContext.expressionList().COMMA();
      } else {
        for (final JavaParser.CasePatternContext cp : labeledRuleContext.casePattern()) {
          caseItems.add(dispatch.visit(cp));
        }
        separators = labeledRuleContext.COMMA();
      }
      if (caseItems.size() > 1) {
        parts.add(
          group(
            concat(Tokens.sourced(labeledRuleContext.CASE()), indent(concat(line(), Tokens.joinSourced(caseItems, separators, line()))))
          )
        );
      } else {
        parts.add(Tokens.sourced(labeledRuleContext.CASE()));
        parts.add(text(" "));
        parts.add(caseItems.getFirst());
      }
    }

    if (labeledRuleContext.guard() != null) {
      parts.add(text(" "));
      parts.add(dispatch.visit(labeledRuleContext.guard()));
    }

    final List<Document> outcomeLeading = dispatch.leading(labeledRuleContext.switchRuleOutcome());
    if (!outcomeLeading.isEmpty()) {
      parts.add(text(" "));
      parts.add(Tokens.sourced(labeledRuleContext.ARROW()));
      parts.add(indent(concat(hardLine(), concat(outcomeLeading), dispatch.visit(labeledRuleContext.switchRuleOutcome()))));
    } else {
      parts.add(text(" "));
      parts.add(Tokens.sourced(labeledRuleContext.ARROW()));
      parts.add(text(" "));
      parts.add(dispatch.visit(labeledRuleContext.switchRuleOutcome()));
    }
    parts.add(dispatch.trailing(labeledRuleContext));
    return group(concat(parts));
  }

  Document visitSwitchRuleOutcome(final JavaParser.SwitchRuleOutcomeContext outcomeContext) {
    if (outcomeContext.block() != null) {
      return dispatch.visit(outcomeContext.block());
    }
    final List<Document> parts = new ArrayList<>();
    for (int i = 0; i < outcomeContext.blockStatement().size(); i++) {
      if (i > 0) {
        parts.add(hardLine());
      }
      parts.add(dispatch.visit(outcomeContext.blockStatement(i)));
    }
    return concat(parts);
  }
}
