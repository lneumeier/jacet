class CallArgCases {

  // args-first-breaking-chain-expands-all: first arg is a breaking chain
  void argsFirstBreakingChain(Builder builder, Config someConfiguration, User user, boolean flag) {
    builder.withValue(
      someConfiguration
        .getReader()
        .getAuthorization(user)
        .stream()
        .filter(a -> !a.isEmpty())
        .toList(),
      flag
    );
  }

  // args-last-breaking-chain-expands-all: last arg is a breaking chain
  void argsLastBreakingChain(Config config, Model userAuthorizationModel, User user) {
    target.accept(
      userAuthorizationModel,
      config
        .getUserAuthorizationApi()
        .getAuthorization(user)
        .stream()
        .filter(a -> !a.isEmpty())
        .toList()
    );
  }

  // args-with-trailing-comments: trailing line comments inside arg lists
  void firstArgComment() {
    service.lookup(
      key, // identifier of the entity in the previous group
      kind
    );
  }

  void middleArgComment() {
    service.process(
      first,
      second, // marker that toggles the alternative path
      third
    );
  }

  void noCommentStaysFlat() {
    service.process(first, second, third);
  }

  // arg-trailing-block-comment-before-comma: block comment between a non-last arg and its comma
  // A block comment between a non-last argument and its following comma must be preserved (it
  // attaches as a trailing comment of that argument) and re-indented so its '*' lines align under
  // the '/*' regardless of the comment's source column.
  void argTrailingBlockCommentBeforeComma(java.util.Optional<String> opt) {
    opt.ifPresentOrElse(
      x -> {
        a(x);
      } /*
       * a multi-line note explaining the first branch
       * spanning two lines
       */,
      () -> {
        b();
      }
    );
  }

  // assignment-method-call-breaks-inside-args: RHS method call breaks inside its args
  void test() {
    final SomeVeryLongTypeName someVeryLongVariableNameThatIsQuiteLong = someMethodWithAReasonablyLongNameToo(argument1, argument2);
    final var shortVar = shortMethod();
    final String simple = computeValue(a, b, c);
    final Map<String, List<SomeVeryLongTypeName>> extremelyLongVariableNameForThisDeclaration = someFactory.createComplicatedInstance(
      firstArg,
      secondArg
    );
  }

  // assignment-single-call-rhs-breaks-args: single-call RHS breaks its args one per line
  // A single-call RHS that doesn't fit keeps the call head on the `=` line and breaks its
  // arguments one per line, rather than breaking after `=`.
  private static final DescribedPredicate<JavaClass> SIMPLE_NAME_ENDS_WITH_ENTITY = DescribedPredicate.describe(
    "have simple name ending with 'Entity'",
    javaClass -> javaClass.getSimpleName().endsWith("Entity")
  );
}
