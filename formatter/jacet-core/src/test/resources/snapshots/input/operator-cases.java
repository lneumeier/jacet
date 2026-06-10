class OperatorCases {

  // binop-chain-operand-breaks: binop RHS/return with breaking chain operand
  // plain assignment (assignment-operator path): binop RHS with breaking chain operand
  void binopChainPlainAssign(ItemSource itemSource) {
    nextNumber = itemSource.findFirstByOrderByNodeIndexDesc().map(ValueNode::getAmount).orElseGet(() -> 0L) + 1;
  }

  // return statement: binop with a breaking chain operand
  long inReturn(ItemSource itemSource) {
    return itemSource.findFirstByOrderByNodeIndexDesc().map(ValueNode::getAmount).orElseGet(() -> 0L) + 1;
  }

  // binary-comparison-chain-operand-hugs-operator: operator + right operand glued to breaking chain
  // A single non-logical operator keeps the operator + right operand glued to the breaking chain's
  // last line: `.orElse(-1) == 0`, not `.orElse(-1) ==` then `0` on its own line.
  boolean isSameNodeResultStatus(Result result, int processRecordIndex, ValueContainer error) {
    return (this.isSameNodeValue(result, error) && result.getProcessRecordIndex().map(index -> index.compareTo(processRecordIndex)).orElse(-1) == 0);
  }

  // A long/breaking RIGHT operand still wraps after the operator (`chain1 +` then chain2).
  long sumOfTwoChains(Service a, Service b) {
    return a.getFirstValue().getSecondValue().getThirdNodeValue().orElse(-1) + b.getFirstValue().getSecondValue().getOtherValue().orElse(2);
  }

  // long-binary-expression: long || chains across return/assign forms
  private boolean returnInParens(final Container container) {
    return (
      container.getTotalAmount().signum() != 0 || container.getSubsetAmount().signum() != 0 || container.getNestedEntryWeightTotal().signum() != 0
    );
  }

  private boolean returnNoParens(final Container container) {
    return container.getTotalAmount().signum() != 0 || container.getSubsetAmount().signum() != 0 || container.getNestedEntryWeightTotal().signum() != 0;
  }

  private void localFinalAssign(final Container container) {
    final boolean result = container.getTotalAmount().signum() != 0 || container.getSubsetAmount().signum() != 0 || container.getNestedEntryWeightTotal().signum() != 0;
  }

  private void localVarAssign(final Container container) {
    var result = container.getTotalAmount().signum() != 0 || container.getSubsetAmount().signum() != 0 || container.getNestedEntryWeightTotal().signum() != 0;
  }

  private void longBinaryPlainAssign(final Container container) {
    boolean result;
    result = container.getTotalAmount().signum() != 0 || container.getSubsetAmount().signum() != 0 || container.getNestedEntryWeightTotal().signum() != 0;
  }

  private void shortStaysFlat(final boolean a, final boolean b) {
    final boolean ok = a || b;
  }

  // paren-binop-nested-operand: parenthesized binop with nested operand breaking/fitting
  boolean nestedOperandBreaks() {
    return (
      element.getProcessType() == ProcessType.MODE &&
      (processFlagType == ProcessFlagType.PRIMARY_CATEGORY ||
        processFlagType == ProcessFlagType.RECURRING_PRIMARY_CATEGORY ||
        processFlagType == ProcessFlagType.BATCH_PREVIEW) &&
      element.getInnerConfig().map(this::isInnerConfigValid).orElse(false)
    );
  }

  boolean nestedOperandFits() {
    return aFairlyShortFlagValue && (secondOption || thirdOption) && lastShortFlagValue;
  }

  // shift-operators: <<, >>, >>> and compound shift assignments
  int unsignedRight(final int value) {
    return value >>> 8;
  }

  int signedRight(final int value) {
    return value >> 4;
  }

  int leftShift(final int value) {
    return value << 2;
  }

  int chained(final int a, final int b) {
    return (a >>> 8) & 0xFF | (b << 16);
  }

  void compoundAssignments(int x) {
    x >>>= 1;
    x >>= 2;
    x <<= 3;
  }

  char extractByte(final int crc) {
    return (char) ((crc >>> 8) & 0xFF);
  }

  // long-assignment: long local declarations, multi-declarators, short assign
  public void localAssign() {
    final ALongClassNameThatTriggersBreaking adapter = new ALongClassNameThatTriggersBreaking(firstArg, secondArg, thirdArg, fourthArg, fifthArg);
  }

  public void multiDeclarators() {
    int a = 1, b = 2, c = 3;
  }

  public void shortAssign() {
    final String x = compute();
  }
}
