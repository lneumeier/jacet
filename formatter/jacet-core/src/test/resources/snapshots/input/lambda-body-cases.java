class LambdaBodyCases {

  // binop-indent: binop body wraps with operands aligned under the arrow
  Predicate<Data> binopIndent() {
    return data -> SomeVeryLongClassName.isConditionAlphaExtended(data) || SomeVeryLongClassName.isConditionBravoExtended(data) || SomeVeryLongClassName.isConditionCharlieExtended(data);
  }

  // boolean-chain: short stays flat
  Predicate<Filter> shortChain() {
    return filter -> filter.equals("a") && filter.equals("b");
  }

  // boolean-chain: long && chain breaks
  Predicate<Filter> longChain() {
    return filter -> "field1".equals(filter.getField()) && filter.getOperator() == Operator.EQUAL && "x".equals(filter.getValue());
  }

  // boolean-chain: long || chain of parenthesized && groups breaks
  Predicate<Filter> longOrChain() {
    return filter -> ("field2".equals(filter.getField()) && "2".equals(filter.getValue())) || ("field3".equals(filter.getField()) && "x".equals(filter.getValue())) || ("field4".equals(filter.getField()) && "y".equals(filter.getValue()));
  }

  // boolean-chain: top-level boolean (not in a lambda) breaks the same way
  boolean topLevelBoolean() {
    final boolean x = thisIsAVeryLongIdentifierA && thisIsAVeryLongIdentifierB && thisIsAVeryLongIdentifierC && thisIsAVeryLongIdentifierD;
    return x;
  }

  // return-body-hang: lambda returned from a method hangs its body under the arrow
  public static <T> Predicate<T> distinctByValues(final Function<? super T, ?>... keyExtractors) {
    final Collection<Object> seen = new HashSet<>();

    return t -> seen.addAll(Stream.of(keyExtractors).map(ke -> ke.apply(t)).toList());
  }

  // return-body-hang: short inline body stays on the arrow line
  Supplier<String> shortInline() {
    return () -> "value";
  }

  // hang-expand-rules: lambda body is BinOp — NOT expandable per prettier-java. All args wrap.
  void binopBody() {
    BaseEntity.unproxyIf(this.selectedEntity, SelectedEntityBaseObject.class, () -> StringUtils.isNotBlank(this.recordNumber) && this.selectedEntityNumber > 0);
  }

  // hang-expand-rules: lambda body is MethodInvocation — expandable. Hang.
  void methodInvocationBody() {
    list.computeIfAbsent(longKeyExpression, longSecondArgExpression, key -> repository.lookupByKeyAndExtraArgumentAndAnotherToForceWrapping(key));
  }

  // hang-expand-rules: lambda body is Ternary — expandable. Hang.
  void ternaryBody() {
    list.process(longFirstArg, secondLongArgument, value -> value > 0 ? handlerForPositiveValuesOnly(value) : handlerForZeroOrNegative(value));
  }

  // hang-expand-rules: single-arg lambda with binop body — fits flat, no hang needed.
  void singleArgBinopShort() {
    stream.filter(x -> x > 0);
  }

  // hug-single-unary-body: parenthesized body hangs under the arrow
  void parenBody(String value) {
    Assert.isTrue(StringUtils.isNumeric(value) || SUPPLEMENTARY_PERIOD_DOMAIN_VALUE.equalsIgnoreCase(value), () -> ("The period may only consist of numeric characters or " + SUPPLEMENTARY_PERIOD_DOMAIN_VALUE + " here."));
  }

  // hug-single-unary-body: negated unary body hangs under the arrow inside a chain
  void unaryBody() {
    return loginHistory.stream().filter(userLoginHistoryEntity -> !(userLoginHistoryEntity.isSuccessful() && !MAX_FAILED_ATTEMPTS_MESSAGE.equals(userLoginHistoryEntity.getLoginReason()))).toList();
  }

  // hug-single-unary-body: assignment body hangs under the arrow
  void assignmentBody() {
    final Advice modelInterceptor = new ModelProxyInterceptor("", (fieldPath, _, _) -> callMapping.modelPath = this.getPathWithoutDomainTypeValueAndSomeMorePaddingHere(fieldPath));
  }

  // hug-single-unary-body: binary body is NOT a single unary — stays expanded with all args broken
  void binaryBodyStaysExpanded(String first, String second) {
    someMethodWithAVeryLongNameHere(firstArgumentValueGoesHereAndIsLong, () -> firstOperandValueIsAlsoQuiteLong + secondOperandValueHereToo);
  }

  // body-comments: comment on its own line between arrow and body
  Stream<String> separateLine(String[] params, String[] duplicates) {
    return Arrays.stream(params).flatMap(change ->
      // cross product (on own line)
      Arrays.stream(duplicates).map(d -> d.toString()));
  }

  // body-comments: trailing comment on the arrow line
  Stream<String> sameLine(String[] params, String[] duplicates) {
    return Arrays.stream(params).flatMap(change -> // cross product (same line as arrow)
      Arrays.stream(duplicates).map(d -> d.toString()));
  }
}
