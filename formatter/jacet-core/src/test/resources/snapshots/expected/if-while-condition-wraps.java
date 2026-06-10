package com.example;

class IfWhileConditionWraps {

  void chainCondition(java.util.stream.Stream<Object> enumConstants, String enumStringValue) {
    if (
      Stream.of(enumConstants)
        .map(String::valueOf)
        .anyMatch(enumName -> enumName.equals(enumStringValue))
    ) {
      doSomething();
    }
  }

  void whileChainCondition(Worklist worklist) {
    while (worklist.pollNextEntry().resolveDependencies().hasUnprocessedEntriesRemainingInTheCurrentBatch(context)) {
      process();
    }
  }

  // short condition stays inline (no wrap)
  void shortCondition(boolean a, boolean b) {
    if (a && b) {
      go();
    }
  }
}
