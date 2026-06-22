package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.breakIndent;
import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.conditionalLayout;
import static de.irotation.jacet.document.Document.empty;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.ifBreak;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.removeLines;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.strictConditionalLayout;
import static de.irotation.jacet.document.Document.text;
import static de.irotation.jacet.document.Document.willBreak;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Group;
import de.irotation.jacet.document.LineSuffix;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats method calls and member-access chains: the chain layout (prettier-java's printMemberChain), method/method-reference calls,
 * argument lists including the expandable-last-lambda hang, the expression list, super-suffix, and explicit generic invocations.
 */
final class InvocationFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;
  private final int printWidth;

  InvocationFormatter(final FormattingDispatch dispatch, final int printWidth) {
    this.dispatch = dispatch;
    this.printWidth = printWidth;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.MethodCallExpressionContext.class, this::visitMethodCallExpression);
    registry.register(JavaParser.MemberReferenceExpressionContext.class, this::visitMemberReferenceExpression);
    registry.register(JavaParser.MethodReferenceExpressionContext.class, this::visitMethodReferenceExpression);
    registry.register(JavaParser.MethodCallContext.class, this::visitMethodCall);
    registry.register(JavaParser.ExpressionListContext.class, this::visitExpressionList);
    registry.register(JavaParser.ArgumentsContext.class, this::visitArguments);
    registry.register(JavaParser.SuperSuffixContext.class, this::visitSuperSuffix);
    registry.register(JavaParser.ExplicitGenericInvocationContext.class, this::visitExplicitGenericInvocation);
    registry.register(JavaParser.ExplicitGenericInvocationSuffixContext.class, this::visitExplicitGenericInvocationSuffix);
  }

  private static boolean isFactoryReceiver(final JavaParser.ExpressionContext receiver) {
    if (!(receiver instanceof final JavaParser.PrimaryExpressionContext primExpr)) {
      return false;
    }
    final JavaParser.PrimaryContext primary = primExpr.primary();
    if (primary.THIS() != null || primary.SUPER() != null) {
      return true;
    }
    if (primary.identifier() != null) {
      final String text = primary.identifier().getText();
      return !text.isEmpty() && Character.isUpperCase(text.charAt(0));
    }
    return false;
  }

  private static int firstMethodCallIndex(final List<JavaParser.MemberReferenceExpressionContext> segments) {
    for (int i = 0; i < segments.size(); i++) {
      if (segments.get(i).methodCall() != null) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Whether any method call in the chain (the receiver call plus every {@code .name(args)} segment) carries a non-simple argument. This is
   * prettier-java's printMemberChain trigger {@code methodInvocations.some(inv => !inv.args.every(isSimpleCallArgument))} — combined with a
   * {@code > 2} call count it force-expands the whole chain one call per line, regardless of width. A lambda, ternary, switch, cast, binary
   * expression, or a {@code new T(...)} / call with more than two arguments all count as non-simple.
   */
  private static boolean anyCallHasNonSimpleArgument(
    final JavaParser.ExpressionContext receiver,
    final List<JavaParser.MemberReferenceExpressionContext> segments
  ) {
    if (receiver instanceof final JavaParser.MethodCallExpressionContext call && callHasNonSimpleArgument(call.methodCall())) {
      return true;
    }
    return segments.stream().anyMatch(s -> s.methodCall() != null && callHasNonSimpleArgument(s.methodCall()));
  }

  private static boolean callHasNonSimpleArgument(final JavaParser.MethodCallContext call) {
    return callArguments(call)
      .stream()
      .anyMatch(arg -> !isSimpleCallArgument(arg, 2));
  }

  private static List<JavaParser.ExpressionContext> callArguments(final JavaParser.MethodCallContext call) {
    if (call == null) {
      return List.of();
    }
    final JavaParser.ArgumentsContext args = call.arguments();
    if (args == null || args.expressionList() == null) {
      return List.of();
    }
    return args.expressionList().expression();
  }

  /**
   * prettier-java's {@code isSimpleCallArgument(node, depth=2)}: a literal/identifier/this, a field/array access or
   * method-reference/unary/update over a simple operand, or a {@code new T(...)} / {@code name(...)} call whose argument count is
   * {@code <= depth} and whose arguments are themselves simple one level shallower. Everything else — lambdas, ternaries, switch
   * expressions, casts, binary/instanceof expressions, type-qualified method refs like {@code String[]::new} — is non-simple. Used to
   * decide member-chain force-expansion. A class literal {@code X.class} is a field access over a type name in prettier's tree, hence
   * simple; an array creator {@code new int[]{...}} is a separate non-simple node; a method reference is simple unless its qualifier is an
   * array type.
   */
  private static boolean isSimpleCallArgument(final JavaParser.ExpressionContext expr, final int depth) {
    if (depth <= 0) {
      return false;
    }
    if (expr instanceof final JavaParser.PrimaryExpressionContext primExpr) {
      final JavaParser.PrimaryContext primary = primExpr.primary();
      return primary.literal() != null || primary.identifier() != null || primary.THIS() != null || primary.CLASS() != null;
    }
    if (expr instanceof final JavaParser.ObjectCreationExpressionContext creation) {
      final JavaParser.CreatorContext creator = creation.creator();
      if (creator.classCreatorRest() == null) {
        return false;
      }
      return argumentsSimple(callArgumentsOf(creator.classCreatorRest().arguments()), depth);
    }
    if (expr instanceof final JavaParser.MethodCallExpressionContext call) {
      return argumentsSimple(callArguments(call.methodCall()), depth);
    }
    if (expr instanceof final JavaParser.MemberReferenceExpressionContext mref) {
      final JavaParser.ExpressionContext object = mref.expression();
      if (mref.methodCall() != null) {
        return (object == null || isSimpleCallArgument(object, depth)) && argumentsSimple(callArguments(mref.methodCall()), depth);
      }
      return object == null || isSimpleCallArgument(object, depth);
    }
    if (expr instanceof final JavaParser.SquareBracketExpressionContext sqb) {
      return isSimpleCallArgument(sqb.expression(0), depth) && isSimpleCallArgument(sqb.expression(1), depth);
    }
    if (expr instanceof final JavaParser.MethodReferenceExpressionContext mrefx) {
      if (mrefx.expression() != null) {
        return isSimpleCallArgument(mrefx.expression(), depth);
      }
      return mrefx.typeType() == null || !mrefx.typeType().getText().contains("[");
    }
    if (expr instanceof final JavaParser.UnaryOperatorExpressionContext unary) {
      return isSimpleCallArgument(unary.expression(), depth);
    }
    if (expr instanceof final JavaParser.PostIncrementDecrementOperatorExpressionContext post) {
      return isSimpleCallArgument(post.expression(), depth);
    }
    return false;
  }

  private static List<JavaParser.ExpressionContext> callArgumentsOf(final JavaParser.ArgumentsContext args) {
    if (args == null || args.expressionList() == null) {
      return List.of();
    }
    return args.expressionList().expression();
  }

  private static boolean argumentsSimple(final List<JavaParser.ExpressionContext> args, final int depth) {
    return args.size() <= depth && args.stream().allMatch(arg -> isSimpleCallArgument(arg, depth - 1));
  }

  /**
   * Whether any method call <em>except the last segment</em> carries a lambda argument. This is the "other calls have function arguments"
   * half of prettier-java's {@code lastGroupWillBreakAndOtherCallsHaveFunctionArguments}: when the last segment breaks (a trailing block
   * lambda) and another call in the chain also takes a lambda, the chain is forced one-call-per-line rather than hugging the last lambda.
   */
  private static boolean otherCallHasLambdaArgument(
    final JavaParser.ExpressionContext receiver,
    final List<JavaParser.MemberReferenceExpressionContext> segments
  ) {
    if (receiver instanceof final JavaParser.MethodCallExpressionContext call && callHasLambdaArgument(call.methodCall())) {
      return true;
    }
    for (int i = 0; i < segments.size() - 1; i++) {
      final JavaParser.MemberReferenceExpressionContext s = segments.get(i);
      if (s.methodCall() != null && callHasLambdaArgument(s.methodCall())) {
        return true;
      }
    }
    return false;
  }

  private static boolean callHasLambdaArgument(final JavaParser.MethodCallContext call) {
    final JavaParser.ArgumentsContext args = call.arguments();
    if (args == null || args.expressionList() == null) {
      return false;
    }
    return args
      .expressionList()
      .expression()
      .stream()
      .anyMatch(expr -> expr instanceof JavaParser.ExpressionLambdaContext);
  }

  Document visitMethodCallExpression(final JavaParser.MethodCallExpressionContext expressionContext) {
    return dispatch.visit(expressionContext.methodCall());
  }

  /**
   * Formats a member reference. An inner segment of a chain (its parent is also a member reference) emits just {@code receiver.suffix} and
   * lets the outermost segment run the full chain layout via {@link #formatChain}.
   */
  Document visitMemberReferenceExpression(final JavaParser.MemberReferenceExpressionContext expressionContext) {
    if (expressionContext.getParent() instanceof JavaParser.MemberReferenceExpressionContext) {
      return concat(
        dispatch.visit(expressionContext.expression()),
        Tokens.sourced(expressionContext.bop),
        this.memberSuffix(expressionContext)
      );
    }
    return this.formatChain(expressionContext);
  }

  /**
   * Lays out a member-access chain ({@code a.b().c().d()}), mirroring prettier-java's primary()/printMemberChain.
   *
   * <p>The shouldNotWrap heuristic is evaluated on groups[0] — the receiver plus any leading field accesses that precede the first method
   * call:
   * <ul>
   *   <li>no leading field access (the receiver itself is groups[0]) → factory iff the receiver is this/super or a capitalized identifier
   *       ({@code Stream.of(x)}, {@code this.foo()}).
   *   <li>leading field accesses present → factory iff the LAST field name before the first call is capitalized
   *       ({@code com.example.Stream.of(x)}). The receiver itself does NOT count here, so {@code this.someRepository.findAll()...} is not a
   *       factory head — its first call wraps to its own line rather than gluing onto {@code this.someRepository}.
   * </ul>
   * When shouldNotWrap holds, the first call binds tightly to the receiver and the chain does not wrap until it has 3+ method calls
   * (cutoff 2 method calls / 3 groups); otherwise even a 2-call chain wraps when it doesn't fit (cutoff 1). At or below the cutoff the chain
   * is emitted linearly and the inner groups (arg lists, lambda bodies) handle their own wrapping.
   *
   * <p>Above the cutoff there are two break drivers (note prettier-java's primary() does NOT use isSimpleCallArgument/shouldNotWrap):
   * <ol>
   *   <li>mustBreakForCallExpressions: more than two method calls AND some call takes a lambda argument → force one call per line regardless
   *       of width.
   *   <li>otherwise a propagating break: a break in the head or a non-last segment forces one-call-per-line; a break in the LAST segment does
   *       not (it falls to {@code conditionalLayout([oneLine, expanded])} so a trailing block lambda hugs), UNLESS another call also takes a
   *       lambda (lastGroupWillBreakAndOtherCallsHaveFunctionArguments).
   * </ol>
   * jacet's printer does not propagate hardLines across group boundaries (containsHardLine stops at a sub-group), so the propagating break is
   * detected here with {@link Document#willBreak} and turned into a hardLine separator — the same visual result as prettier's breakParent.
   * Each segment's suffix and comments are printed exactly once; {@code dispatch.visit} consumes comments from the hidden channel, so visiting
   * a segment twice would drop or double-emit them.
   *
   * <p>When the receiver carries a trailing line comment (rendered as a LineSuffix, so {@code willBreak} is true) the chain breaks before
   * every segment's dot regardless of the cutoff, so the comment stays on the receiver's line; otherwise it would be flushed onto the line of
   * an opening bracket and re-home on the next format pass, breaking idempotence.
   */
  private Document formatChain(final JavaParser.MemberReferenceExpressionContext top) {
    final List<JavaParser.MemberReferenceExpressionContext> segments = new ArrayList<>();
    JavaParser.ExpressionContext cur = top;
    while (cur instanceof final JavaParser.MemberReferenceExpressionContext mre) {
      segments.addFirst(mre);
      cur = mre.expression();
    }
    final Document receiver = dispatch.visit(cur);
    if (dispatch.hasTrailing(cur) && willBreak(receiver)) {
      final List<Document> tail = new ArrayList<>();
      for (final JavaParser.MemberReferenceExpressionContext segment : segments) {
        tail.add(hardLine());
        tail.add(this.printChainSegment(segment));
      }
      return new Group(concat(receiver, breakIndent(concat(tail))), true);
    }
    if (segments.size() < 2) {
      return concat(receiver, Tokens.sourced(segments.getFirst().bop), this.memberSuffix(segments.getFirst()));
    }

    final int firstMethodIdx = firstMethodCallIndex(segments);
    final boolean shouldNotWrap =
      firstMethodIdx == 0 ? isFactoryReceiver(cur) : firstMethodIdx > 0 && hasFactoryFieldAccessHead(segments, firstMethodIdx);
    final long methodCallCount =
      (cur instanceof JavaParser.MethodCallExpressionContext ? 1 : 0) + segments
        .stream()
        .filter(s -> s.methodCall() != null)
        .count();
    final long methodCallCutoff = shouldNotWrap ? 2 : 1;
    if (methodCallCount <= methodCallCutoff) {
      final List<Document> linearParts = new ArrayList<>();
      linearParts.add(receiver);
      for (final JavaParser.MemberReferenceExpressionContext segment : segments) {
        linearParts.add(Tokens.sourced(segment.bop));
        linearParts.add(this.memberSuffix(segment));
        linearParts.add(dispatch.trailing(segment));
      }
      return concat(linearParts);
    }

    final int boundEnd = firstMethodIdx >= 0 ? firstMethodIdx + (shouldNotWrap ? 1 : 0) : 0;

    final Document headGroup = this.printChainHead(receiver, segments, boundEnd);
    final List<Document> tailGroups = new ArrayList<>();
    for (int i = boundEnd; i < segments.size(); i++) {
      tailGroups.add(this.printChainSegment(segments.get(i)));
    }

    final boolean mustBreak = methodCallCount > 2 && anyCallHasNonSimpleArgument(cur, segments);
    final int lastTail = tailGroups.size() - 1;
    final boolean headOrMiddleBreaks =
      willBreak(headGroup) || tailGroups.subList(0, Math.max(0, lastTail)).stream().anyMatch(Document::willBreak);
    final boolean lastBreaksWithOtherLambda =
      lastTail >= 0 && willBreak(tailGroups.get(lastTail)) && otherCallHasLambdaArgument(cur, segments);
    final boolean expand = mustBreak || headOrMiddleBreaks || lastBreaksWithOtherLambda;

    final List<Document> expandedTail = new ArrayList<>();
    for (final Document tailGroup : tailGroups) {
      expandedTail.add(hardLine());
      expandedTail.add(tailGroup);
    }
    final Document expanded = concat(headGroup, breakIndent(concat(expandedTail)));
    if (expand) {
      return new Group(expanded, true);
    }
    final List<Document> oneLineParts = new ArrayList<>();
    oneLineParts.add(headGroup);
    oneLineParts.addAll(tailGroups);
    return conditionalLayout(concat(oneLineParts), expanded);
  }

  /**
   * Prints the receiver plus the {@code boundEnd} leading segments that stay on the first line (field accesses and, when shouldNotWrap, the
   * first method call). This is prettier's first member-chain group — the one the indented tail hangs off. A trailing same-line comment
   * after a segment ({@code .skip(1) // why}) is emitted as a LineSuffix that flushes at the next line break, keeping it glued to its
   * segment.
   */
  private Document printChainHead(
    final Document receiver,
    final List<JavaParser.MemberReferenceExpressionContext> segments,
    final int boundEnd
  ) {
    final List<Document> parts = new ArrayList<>();
    parts.add(receiver);
    for (int i = 0; i < boundEnd; i++) {
      parts.add(Tokens.sourced(segments.get(i).bop));
      parts.add(this.memberSuffix(segments.get(i)));
      parts.add(dispatch.trailing(segments.get(i)));
    }
    return concat(parts);
  }

  /**
   * Prints one tail segment ({@code .name(args)}) together with any leading comment on its dot. Each tail segment is a member-chain group;
   * its {@link Document#willBreak} status drives whether the whole chain expands. A leading comment on the dot — a comment on its own line
   * between chain calls — reaches the enclosing chain group directly (not nested in a sub-group), forcing the break that keeps the comment
   * with the segment it leads.
   */
  private Document printChainSegment(final JavaParser.MemberReferenceExpressionContext segment) {
    final List<Document> parts = new ArrayList<>(dispatch.leadingComments(segment.bop.getTokenIndex()));
    parts.add(Tokens.sourced(segment.bop));
    parts.add(this.memberSuffix(segment));
    parts.add(dispatch.trailing(segment));
    return concat(parts);
  }

  /**
   * Detects the prettier shouldNotWrap pattern where the receiver expression is a sequence of field accesses ending in a capitalized
   * identifier, like {@code com.example.Stream.of(x)}. The capitalized identifier signals "namespace before factory call".
   */
  private static boolean hasFactoryFieldAccessHead(
    final List<JavaParser.MemberReferenceExpressionContext> segments,
    final int firstMethodIdx
  ) {
    if (firstMethodIdx <= 0) {
      return false;
    }
    final JavaParser.MemberReferenceExpressionContext lastBeforeCall = segments.get(firstMethodIdx - 1);
    if (lastBeforeCall.identifier() == null) {
      return false;
    }
    final String name = lastBeforeCall.identifier().getText();
    return !name.isEmpty() && Character.isUpperCase(name.charAt(0));
  }

  private Document memberSuffix(final JavaParser.MemberReferenceExpressionContext segment) {
    if (segment.methodCall() != null) {
      return dispatch.visit(segment.methodCall());
    }
    if (segment.identifier() != null) {
      return dispatch.visit(segment.identifier());
    }
    if (segment.THIS() != null) {
      return Tokens.sourced(segment.THIS());
    }
    if (segment.SUPER() != null) {
      if (segment.superSuffix() != null) {
        return concat(Tokens.sourced(segment.SUPER()), dispatch.visit(segment.superSuffix()));
      }
      return Tokens.sourced(segment.SUPER());
    }
    if (segment.NEW() != null && segment.innerCreator() != null) {
      return concat(Tokens.sourced(segment.NEW()), text(" "), dispatch.visit(segment.innerCreator()));
    }
    if (segment.explicitGenericInvocation() != null) {
      return dispatch.visit(segment.explicitGenericInvocation());
    }
    throw new IllegalStateException("Unhandled member reference at " + segment.getStart().getLine());
  }

  Document visitMethodReferenceExpression(final JavaParser.MethodReferenceExpressionContext expressionContext) {
    final List<Document> parts = new ArrayList<>();
    if (expressionContext.expression() != null) {
      parts.add(dispatch.visit(expressionContext.expression()));
    } else if (expressionContext.typeType() != null) {
      parts.add(dispatch.visit(expressionContext.typeType()));
    } else if (expressionContext.classType() != null) {
      parts.add(dispatch.visit(expressionContext.classType()));
      if (expressionContext.SUPER() != null) {
        parts.add(Tokens.sourced(expressionContext.DOT()));
        parts.add(Tokens.sourced(expressionContext.SUPER()));
      }
    }
    parts.add(Tokens.sourced(expressionContext.COLONCOLON()));
    if (expressionContext.typeArguments() != null) {
      parts.add(dispatch.visit(expressionContext.typeArguments()));
    }
    if (expressionContext.identifier() != null) {
      parts.add(dispatch.visit(expressionContext.identifier()));
    } else if (expressionContext.NEW() != null) {
      parts.add(Tokens.sourced(expressionContext.NEW()));
    }
    return concat(parts);
  }

  Document visitMethodCall(final JavaParser.MethodCallContext callContext) {
    final List<Document> parts = new ArrayList<>();
    if (callContext.identifier() != null) {
      parts.add(dispatch.visit(callContext.identifier()));
    } else if (callContext.THIS() != null) {
      parts.add(Tokens.sourced(callContext.THIS()));
    } else if (callContext.SUPER() != null) {
      parts.add(Tokens.sourced(callContext.SUPER()));
    }
    parts.add(dispatch.visit(callContext.arguments()));
    return concat(parts);
  }

  /**
   * Formats a comma-separated expression list (argument lists, {@code for} update lists). Each argument's trailing comment is rendered (not
   * just the last): a comment sitting between an argument and its following comma attaches as the trailing comment of that argument and
   * would otherwise be dropped for all but the final one. A line-suffix comment after a comma forces a hardLine so the comment keeps its
   * argument association instead of being carried to the line end after a collapse.
   */
  Document visitExpressionList(final JavaParser.ExpressionListContext listContext) {
    final List<JavaParser.ExpressionContext> expressions = listContext.expression();
    final List<TerminalNode> commas = listContext.COMMA();
    final List<Document> parts = new ArrayList<>(dispatch.leading(listContext));
    for (int i = 0; i < expressions.size(); i++) {
      if (i > 0) {
        final int commaIdx = commas
          .get(i - 1)
          .getSymbol()
          .getTokenIndex();
        final Document commaTrailing = dispatch.trailingComment(commaIdx);
        parts.add(Tokens.sourced(commas.get(i - 1)));
        parts.add(commaTrailing);
        parts.add(commaTrailing instanceof LineSuffix ? hardLine() : line());
      }
      parts.addAll(dispatch.leading(expressions.get(i)));
      parts.add(dispatch.visit(expressions.get(i)));
      parts.add(dispatch.trailing(expressions.get(i)));
    }
    parts.add(dispatch.trailing(listContext));
    return concat(parts);
  }

  /**
   * Formats an argument list. When the last argument is an expandable lambda or non-empty array creator, defers to the hang layout.
   * Otherwise the outer group is forced to break when any argument (or the list) carries a trailing comment — else the comment, rendered as
   * a LineSuffix, is flushed past the closing {@code )} and detaches from its argument — or when any argument itself will break:
   * prettier-java's argumentList puts every argument on its own line once a non-huggable argument breaks
   * ({@code !isHuggable || willBreak(last)}), and jacet's printer doesn't propagate a hardLine across the argument's own group boundary, so
   * {@code m(simpleArg, chain.that().breaks())} would otherwise keep the head args on the {@code (} line and hug the breaking chain.
   */
  Document visitArguments(final JavaParser.ArgumentsContext argumentsContext) {
    if (argumentsContext.expressionList() == null) {
      return concat(Tokens.sourced(argumentsContext.LPAREN()), Tokens.sourced(argumentsContext.RPAREN()));
    }
    final List<JavaParser.ExpressionContext> exprs = argumentsContext.expressionList().expression();
    if (!exprs.isEmpty() && exprs.getLast() instanceof final JavaParser.ExpressionLambdaContext lambdaExpr && shouldExpandLastArg(exprs)) {
      return this.formatArgumentsExpandableLast(argumentsContext, exprs, lambdaExpr.lambdaExpression());
    }
    final Document list = dispatch.visit(argumentsContext.expressionList());
    final boolean anyArgHasTrailing =
      exprs.stream().anyMatch(dispatch::hasTrailing) || dispatch.hasTrailing(argumentsContext.expressionList());
    final boolean anyArgWillBreak = willBreak(list);
    final Document contents = concat(
      Tokens.sourced(argumentsContext.LPAREN()),
      breakIndent(concat(softLine(), list)),
      softLine(),
      Tokens.sourced(argumentsContext.RPAREN())
    );
    if (anyArgHasTrailing || anyArgWillBreak) {
      return new Group(contents, true);
    }
    return group(contents);
  }

  /**
   * prettier-java's {@code shouldExpandLastArg}: whether the trailing argument qualifies for the lambda-hang / array-hang layout. Excludes
   * the cases where the penultimate argument has the same type as the last, and the two-argument {@code (lambda, lambda)} shape.
   *
   * <p>TODO: prettier-java additionally excludes args with leading/trailing comments — jacet doesn't expose per-expression comment
   * metadata
   * here yet.
   */
  private static boolean shouldExpandLastArg(final List<JavaParser.ExpressionContext> args) {
    if (args.isEmpty()) {
      return false;
    }
    final JavaParser.ExpressionContext last = args.getLast();
    if (!couldExpandArg(last, false)) {
      return false;
    }
    final JavaParser.ExpressionContext penultimate = args.size() >= 2 ? args.get(args.size() - 2) : null;
    if (penultimate != null && penultimate.getClass().equals(last.getClass())) {
      return false;
    }
    return !(args.size() == 2 && penultimate instanceof JavaParser.ExpressionLambdaContext);
  }

  /**
   * prettier-java 2.8.1 {@code couldExpandArg}/{@code isHuggable}: a non-empty array creation hugs, as does an expression-bodied lambda
   * whose body is a block, an array creation, another huggable lambda, a ternary, or a "single unary" expression. In chevrotain's grammar
   * an assignment ({@code =}, {@code +=}, ...) or {@code instanceof} captures its right-hand side as {@code expression}/{@code pattern}
   * rather than a second {@code unaryExpression}, so those count as single-unary and still hug; only an arithmetic, logical, comparison or
   * shift binary (two or more unary operands) is excluded. Everything else — a primary, parenthesized expression, prefix unary, cast,
   * method call, member reference, object creation, switch expression — is a single unary and hugs.
   */
  private static boolean couldExpandArg(final JavaParser.ExpressionContext arg, final boolean lambdaChainRecursion) {
    if (isNonEmptyArrayCreation(arg)) {
      return true;
    }
    if (!(arg instanceof final JavaParser.ExpressionLambdaContext lambdaExpr)) {
      return false;
    }
    final JavaParser.LambdaBodyContext body = lambdaExpr.lambdaExpression().lambdaBody();
    if (body.block() != null) {
      return true;
    }
    final JavaParser.ExpressionContext bodyExpr = body.expression();
    if (bodyExpr == null) {
      return false;
    }
    if (isArrayCreation(bodyExpr)) {
      return true;
    }
    if (bodyExpr instanceof JavaParser.ExpressionLambdaContext) {
      return couldExpandArg(bodyExpr, true);
    }
    if (lambdaChainRecursion) {
      return false;
    }
    if (bodyExpr instanceof JavaParser.TernaryExpressionContext) {
      return true;
    }
    if (bodyExpr instanceof final JavaParser.BinaryOperatorExpressionContext bin) {
      return Operators.isAssignmentOperator(Operators.binaryOpText(bin));
    }
    return true;
  }

  private static boolean isArrayCreation(final JavaParser.ExpressionContext expr) {
    return expr instanceof final JavaParser.ObjectCreationExpressionContext create && create.creator().arrayCreatorRest() != null;
  }

  private static boolean isNonEmptyArrayCreation(final JavaParser.ExpressionContext expr) {
    if (!(expr instanceof final JavaParser.ObjectCreationExpressionContext create)) {
      return false;
    }
    if (create.creator().arrayCreatorRest() == null) {
      return false;
    }
    final JavaParser.ArrayInitializerContext init = create.creator().arrayCreatorRest().arrayInitializer();
    return init != null && !init.variableInitializer().isEmpty();
  }

  /**
   * Layout for a call whose last arg is an expandable lambda. Builds two layouts and selects via
   * {@link de.irotation.jacet.document.ConditionalLayout}: the hanging-lambda primary keeps the signature inline with the body wrapping
   * inside the parens; the all-break fallback puts every argument on its own line. Each argument is visited exactly once and the resulting
   * docs are shared between both layouts so comments aren't double-emitted or lost.
   *
   * <p>If any non-last argument itself contains a hard newline (e.g. it's a multi-line lambda), the hanging primary's signature line would
   * include that newline, defeating the layout — so the conditional is skipped and the all-break layout is forced directly.
   *
   * <p>Layout selection:
   * <ul>
   *   <li>block body: {@code conditionalLayout} decides on the FIRST line only (its hardLineFits probe stops at the block's opening hardLine),
   *       so the lambda hugs as long as {@code (headArgs, params -> {} fits — a line inside the block exceeding printWidth is identical either
   *       way. {@code strictConditionalLayout} would wrongly expand-all on such a line.
   *   <li>lone expression-bodied lambda (no head args): the hang group is returned directly so its own fits-check sees the trailing content
   *       ({@code ;}, a chain suffix); rendering it in isolation via strictConditionalLayout would miss that suffix and leave a one-char-over
   *       line unbroken.
   *   <li>otherwise: {@code strictConditionalLayout}.
   * </ul>
   */
  private Document formatArgumentsExpandableLast(
    final JavaParser.ArgumentsContext argumentsContext,
    final List<JavaParser.ExpressionContext> args,
    final JavaParser.LambdaExpressionContext lambda
  ) {
    final List<TerminalNode> commas = argumentsContext.expressionList().COMMA();
    final Document lparen = Tokens.sourced(argumentsContext.LPAREN());
    final Document rparen = Tokens.sourced(argumentsContext.RPAREN());
    final List<Document> nonLastArgDocs = new ArrayList<>();
    for (int i = 0; i < args.size() - 1; i++) {
      nonLastArgDocs.add(dispatch.visit(args.get(i)));
    }
    final Document lambdaParams = dispatch.visit(lambda.lambdaParameters());
    final Document arrow = Tokens.sourced(lambda.ARROW());
    final Document arrowTrailing = dispatch.trailingComment(lambda.ARROW().getSymbol().getTokenIndex());
    final Document lambdaBody = dispatch.visit(lambda.lambdaBody());
    final boolean blockBody = lambda.lambdaBody().block() != null;

    final boolean anyNonLastArgBreaks = nonLastArgDocs.stream().anyMatch(Document::containsHardLine);
    if (anyNonLastArgBreaks || this.signatureExceedsPrintWidth(nonLastArgDocs, lambdaParams)) {
      return this.buildAllBreakArgs(lparen, rparen, commas, nonLastArgDocs, lambdaParams, arrow, lambdaBody, blockBody, arrowTrailing);
    }
    final Document primary = this.buildHangingArgs(
      lparen,
      rparen,
      commas,
      nonLastArgDocs,
      lambdaParams,
      arrow,
      lambdaBody,
      blockBody,
      arrowTrailing
    );
    final Document fallback = this.buildAllBreakArgs(
      lparen,
      rparen,
      commas,
      nonLastArgDocs,
      lambdaParams,
      arrow,
      lambdaBody,
      blockBody,
      arrowTrailing
    );
    if (blockBody) {
      return conditionalLayout(primary, fallback);
    }
    if (nonLastArgDocs.isEmpty()) {
      return primary;
    }
    return strictConditionalLayout(primary, fallback);
  }

  private boolean signatureExceedsPrintWidth(final List<Document> nonLastArgDocs, final Document lambdaParams) {
    int width = 1;
    for (int i = 0; i < nonLastArgDocs.size(); i++) {
      if (i > 0) {
        width += 2;
      }
      final int argWidth = Document.flatWidth(nonLastArgDocs.get(i));
      if (argWidth < 0) {
        return true;
      }
      width += argWidth;
    }
    width += 2;
    final int paramsWidth = Document.flatWidth(lambdaParams);
    if (paramsWidth < 0) {
      return true;
    }
    width += paramsWidth + 3;
    return width > printWidth;
  }

  /**
   * Hugging layout: {@code (headArgs, params -> body)}. The lambda parameters are printed with {@code removeLines} so they stay flat —
   * otherwise the params group could break to make the signature line appear to fit, wrongly selecting the hug; flattening lets the
   * strictConditionalLayout measure the true single-line signature width and fall back to all-args-expanded on overflow. For an expression
   * body the body hangs under the arrow ({@code (arg ->\n  body\n)}) when it breaks: jacet's printer doesn't propagate the body's forced
   * break across its group boundary, so {@code bodyBreaks} forces the hang (also covering an arrow trailing comment, whose LineSuffix would
   * otherwise be flushed past the closing {@code )} and {@code ;}).
   */
  private Document buildHangingArgs(
    final Document lparen,
    final Document rparen,
    final List<TerminalNode> commas,
    final List<Document> nonLastArgDocs,
    final Document lambdaParams,
    final Document arrow,
    final Document lambdaBody,
    final boolean blockBody,
    final Document arrowTrailing
  ) {
    final List<Document> signature = new ArrayList<>();
    for (int i = 0; i < nonLastArgDocs.size(); i++) {
      signature.add(nonLastArgDocs.get(i));
      signature.add(Tokens.sourced(commas.get(i)));
      signature.add(text(" "));
    }
    signature.add(removeLines(lambdaParams));
    signature.add(text(" "));
    signature.add(arrow);
    signature.add(arrowTrailing);
    if (blockBody) {
      return concat(lparen, concat(signature), text(" "), lambdaBody, rparen);
    }
    final boolean bodyBreaks = willBreak(lambdaBody) || willBreak(arrowTrailing);
    final Document hanging = concat(
      lparen,
      concat(signature),
      ifBreak(indent(concat(hardLine(), lambdaBody)), concat(text(" "), lambdaBody)),
      ifBreak(hardLine(), empty()),
      rparen
    );
    return new Group(hanging, bodyBreaks);
  }

  private Document buildAllBreakArgs(
    final Document lparen,
    final Document rparen,
    final List<TerminalNode> commas,
    final List<Document> nonLastArgDocs,
    final Document lambdaParams,
    final Document arrow,
    final Document lambdaBody,
    final boolean blockBody,
    final Document arrowTrailing
  ) {
    final Document lambdaFull = blockBody
      ? concat(lambdaParams, text(" "), arrow, text(" "), arrowTrailing, lambdaBody)
      : new Group(
          concat(lambdaParams, text(" "), arrow, arrowTrailing, indent(concat(line(), lambdaBody))),
          willBreak(lambdaBody) || willBreak(arrowTrailing)
        );
    final List<Document> allArgs = new ArrayList<>(nonLastArgDocs);
    allArgs.add(lambdaFull);
    return new Group(concat(lparen, indent(concat(hardLine(), Tokens.joinSourced(allArgs, commas, hardLine()))), hardLine(), rparen), true);
  }

  Document visitExplicitGenericInvocation(final JavaParser.ExplicitGenericInvocationContext invocationContext) {
    return concat(
      dispatch.visit(invocationContext.nonWildcardTypeArguments()),
      dispatch.visit(invocationContext.explicitGenericInvocationSuffix())
    );
  }

  Document visitExplicitGenericInvocationSuffix(final JavaParser.ExplicitGenericInvocationSuffixContext suffixContext) {
    if (suffixContext.SUPER() != null) {
      return concat(Tokens.sourced(suffixContext.SUPER()), dispatch.visit(suffixContext.superSuffix()));
    }
    return concat(dispatch.visit(suffixContext.identifier()), dispatch.visit(suffixContext.arguments()));
  }

  Document visitSuperSuffix(final JavaParser.SuperSuffixContext suffixContext) {
    if (suffixContext.arguments() != null && suffixContext.identifier() == null) {
      return dispatch.visit(suffixContext.arguments());
    }
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(suffixContext.DOT()));
    if (suffixContext.typeArguments() != null) {
      parts.add(dispatch.visit(suffixContext.typeArguments()));
    }
    parts.add(dispatch.visit(suffixContext.identifier()));
    if (suffixContext.arguments() != null) {
      parts.add(dispatch.visit(suffixContext.arguments()));
    }
    return concat(parts);
  }
}
