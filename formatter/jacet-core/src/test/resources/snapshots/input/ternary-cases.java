class TernaryCases {

  // ternary-as-method-arg: ternary passed as a method argument
  void emit(final String value) {}

  void run(final boolean flag) {
    emit(flag ? "yes" : "no");
  }

  // ternary-assignment-rhs: RHS of assignment, various breaking behaviors
  void rhsFitsOnItsOwnLine() {
    final BigDecimal resultAmount =
      recordEntryType == RecordEntryType.STANDARD ? sourceDataValue.getAmounts().getNetEntryVal() : BigDecimal.ZERO;
  }

  void andConditionRhsFitsOnItsOwnLine() {
    final String localeKey =
      groupTypeFlag == GroupTypeFlag.VARIANT && entryFieldType == EntryFieldType.SELECTED_VALUE ? Constants.VARIANT_ID : localeName;
  }

  void shortStaysInline() {
    final int small = cond ? a : b;
  }

  void ternaryTooLongBreaksArms() {
    final String doesNotFit =
      someReallyLongConditionThatGoesOnAndOnAndOnAndOn == theOtherSide
        ? thisIsAlsoAVeryLongConsequentValueHere
        : alternativeValueThatIsAlsoLong;
  }

  // ternary-in-parens: parenthesized ternary in assignment / arg / chain
  // Parenthesized-ternary RHS: the `(` hugs the condition and the ternary breaks internally
  // (cond \n ? a \n : b), matching prettier-java; the assignment keeps the RHS attached to `=`.
  void parenTernary() {
    final String value = (isConditionMet ? someVeryLongValueThatNeedsWrappingBecauseItExceedsTheLineWidth : anotherVeryLongAlternativeValueThatAlsoExceeds);
  }

  // Ternary in parens used as method argument
  void parenTernaryAsArg() {
    process((isConditionMet ? someVeryLongValueThatNeedsWrappingBecauseItExceedsTheLineWidth : anotherVeryLongAlternativeValueThatAlsoExceeds));
  }

  // Ternary in parens with chain — (cond ? a : b).method()
  void parenTernaryWithChain() {
    final String result = (isConditionMet ? getFirstOptionValueFromConfiguration(config) : getSecondOptionValueFromConfiguration(config)).toString().trim();
  }

  // ternary-return-binary-condition: return/throw with binary-condition ternary
  // Return with binary condition ternary — prettier-java wraps condition in indent() for return context
  String compute(int a, int b, int c, int d) {
    return a > b && c > d && someOtherConditionThatMakesThisLineExceedOneHundredAndFortyCharactersInWidth ? "first branch value result" : "second branch value result";
  }

  // Throw with binary condition ternary
  void validate(int value) {
    throw value > 0 && value < maxThreshold && isValidationEnabled ? new IllegalArgumentException("Value " + value + " out of range for the configured maximum threshold") : new IllegalStateException("Validation is disabled but value check was triggered unexpectedly");
  }

  // Return with simple (non-binary) condition — should NOT get the special indent treatment
  String simple(boolean flag) {
    return flag ? computeVeryLongResultForTrueCase(inputA, inputB, inputC) : computeVeryLongResultForFalseCase(inputD, inputE, inputF);
  }

  // ternary-return-branch-breaking-chain: return ternary whose branch is a breaking chain
  String getValue(NodeId nodeId, GroupId groupId, ConfigType configType) {
    return configType == ConfigType.PRIMARY ? nodeConfiguration.getPrimaryNodeValue(nodeId) : groupService.getGroup(groupId).map(GroupEntity::getChildNodeValue).orElseThrow(() -> new InvalidInputException(ErrorCode.GROUPS_NOT_FOUND));
  }

  // ternary-rhs-binary-condition: binary condition forces break after =
  // Binary condition → always break after = (prettier-java special case)
  void binaryCondition() {
    final String result = someValue > 0 && someOtherConditionThatMakesTheLineVeryLongSoItWraps ? "positive" : "non-positive";
  }

  // Binary condition that wraps — must break after = and indent condition+arms together
  void longBinaryCondition() {
    final String longVariableName = someConditionPartOne > 0 && someConditionPartTwoThatIsAlsoVeryLongAndMakesTheWholeThingExceedTheLineWidthLimit ? "the first branch value" : "the second branch value";
  }

  // Simple binary condition that fits inline entirely
  void simpleBinaryInline() {
    final int x = a > b ? a : b;
  }

  // ternary-rhs-condition-fits-on-eq-line: condition fits on = line, arms may wrap
  // Condition fits on = line — should stay there
  void simpleCondition() {
    final String result = isEnabled ? "yes" : "no";
  }

  // Condition is short enough, arms are long → condition on = line, arms wrap below
  void shortConditionLongArms() {
    final String value = isEnabled
      ? someVeryLongMethodCallThatNeedsWrapping(argument1, argument2, argument3)
      : anotherVeryLongMethodCallThatAlsoNeedsWrapping(argument1, argument2);
  }

  // Variable assignment with condition that fits
  void variableAssignment() {
    final int timeout = connectionConfig.isRetryEnabled() ? connectionConfig.getRetryTimeout() : DEFAULT_TIMEOUT;
  }

  // Field initializer
  private final String mode = useNewMode ? "new" : "legacy";

  // Return ternary — not an assignment, should keep current behavior
  String getMode() {
    return useNewMode ? "new" : "legacy";
  }

  // ternary-rhs-of-assignment-fits: short ternary RHS stays inline
  String computeFits(final boolean flag) {
    final String result = flag ? "yes" : "no";
    return result;
  }

  // ternary-rhs-of-assignment-long: long ternary RHS of assignment
  String computeLong() {
    final String result = veryLongCondition.method().method().anotherCallIsXYZ().getOtherXYZAB() ? firstValueLongNameXX : secondValueShorter;
    return result;
  }

  // nested-ternary: chained / nested ternaries co-break across levels
  // Nested ternary — prettier-java breaks all levels together (no inner group)
  String classify(int value) {
    return value > 100 ? "high" : value > 50 ? "medium" : value > 10 ? "low" : "very low";
  }

  // Deep nesting with long arms — should break all levels together when parent breaks
  String deepClassify(int value) {
    final String result = value > 1000 ? computeVeryHighCategory(value, options) : value > 500 ? computeHighCategory(value, options) : value > 100 ? computeMediumCategory(value, options) : computeLowCategory(value, options);
    return result;
  }

  // Nested ternary as variable initializer — both levels should co-break
  void nestedAssignment() {
    final String label = isSpecial ? (isUrgent ? "URGENT-SPECIAL" : "NORMAL-SPECIAL") : (isUrgent ? "URGENT-REGULAR" : "NORMAL-REGULAR");
  }

  // paren-ternary-receiver-hugs-open-paren: parenthesized ternary as chain receiver
  // A parenthesized ternary used as a chain receiver hugs the open paren (`(cond`) and breaks the
  // ternary at ?/:, rather than breaking after the `(`.
  void parenTernaryReceiverHugsOpenParen() {
    dto.setX(
      (selectionType == GroupSelectionType.PRIMARY_RECORD
        ? primaryRecordSelection.flatMap(PrimaryRecordSelectionEntity::getChildGroup)
        : secondaryItem.getChildGroup()
      )
        .map(ChildGroupBaseEntity::getId)
        .map(ChildGroupEntityId::getNumber)
        .orElse(null)
    );
  }
}
