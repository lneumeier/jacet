class LambdaArgCases {

  // arg-hug-block: a block-bodied lambda as the trailing arg of a chain hugs `(model -> {` even when
  // a line inside the block exceeds printWidth (that inner line is identical whether hugged or expanded).
  void blockHugInChain(Object adapter, Object view, Object parentDocumentReferenceId) {
    assertThat(adapter.toModel(view)).satisfies(model -> {
      assertThat(model).extracting(DocumentNoteDto::getReferenceType).isEqualTo(DocumentBaseObject.DOCUMENT);
      assertThat(model).extracting(DocumentNoteDto::getReference).isEqualTo(parentDocumentReferenceId);
    });
  }

  // arg-hang-body: a lone expression-bodied lambda whose hugged line would exceed printWidth hangs its
  // body under the arrow (`() ->\n  body\n)`) rather than staying on an over-long line.
  Object hangBodyWhenOverflow(java.util.Optional<Config> dbConfiguration, Config config) {
    if (flag) {
      return dbConfiguration
        .stream()
        .filter(c -> c.matches())
        .findFirst()
        .orElseThrow(() ->
          new AppRuntimeException(String.format("Configuration entry '%s' could not be located!", config.getOptionName()))
        );
    }
    return null;
  }

  // arg-signature-overflow: short lambda body fits, but the preceding args overflow so all args expand
  Set<Change> shortBodyExpandsAllArgs(ValidateMetricRows validateMetricRows) {
    return Optionals.mapIfAllPresent(
      validateMetricRows.getMetricRowsOriginal(),
      validateMetricRows.getMetricRowsUpdated(),
      (original, updated) -> Set.of(of(A, original.getA(), updated.getA()), of(B, original.getB(), updated.getB()))
    );
  }

  // arg-signature-overflow: breaking lambda body hangs under the arrow while preceding args stay broken
  Set<Change> breakingBodyHangsUnderArrow(ValidateMetricRows validateMetricRows) {
    return Optionals.mapIfAllPresent(
      validateMetricRows.getMetricRowsOriginal(),
      validateMetricRows.getMetricRowsUpdated(),
      (original, updated) ->
        Set.of(
          of(ROW_SPAN_TOTAL_1, original.getRowSpanTotal1(), updated.getRowSpanTotal1(), validateMetricRows, allowedFunc), // hint
          of(ROW_SPAN_TOTAL_2, original.getRowSpanTotal2(), updated.getRowSpanTotal2(), validateMetricRows, allowedFunc)
        )
    );
  }

  // hanging-long-preceding-args: long preceding args force the trailing block lambda to hang
  void hangingLongPrecedingArgs(final Service service) {
    service.runAndNotifyEverybodyAboutEventVeryThoroughly(
      "first-very-long-arg-here",
      "second-very-long-arg-here",
      "third-very-long-arg-here",
      () -> {
        doSomething();
        doMore();
      }
    );
  }

  // hanging-short-preceding-args: short preceding arg, trailing block lambda hugs
  void hangingShortPrecedingArgs(final Service service) {
    service.runAndNotify("first", () -> {
      doSomething();
      doMore();
    });
  }

  // middle-lambda-multiline-body: a lambda in the middle with a body that wraps internally forces all-break
  void middleLambdaBreaks() {
    return batchService.runAndNotify(
      directories.getImportFileList(),
      (final File file) ->
        ErrorHandling.run(
          () -> innerImportBatchService.runBatchStep(directories, file, userId),
          (throwable, errorCode, errorValue) -> NestedProcessingBatchResult.importError(file, errorCode, errorValue)
        ),
      new BatchServiceNotificationParameter(batchId, "import"),
      _ -> this.deleteProcessedDirectories(directories)
    );
  }

  // middle-trivial-lambda-fits: a short middle lambda stays in hanging layout
  void trivialMiddleLambda() {
    return service.process(items, x -> x.getId(), "default", result ->
      handleLongResultProcessingWithMultipleSteps(result, options, context)
    );
  }

  // multi-lambda: two block lambdas as non-adjacent args
  void multiLambda(final Service service) {
    service.runAndNotify(
      arg1,
      () -> {
        doFirstLambdaWork();
      },
      arg3,
      () -> {
        doSecondLambdaWork();
      }
    );
  }

  // block-lambda-non-last-arg: block lambda followed by a trailing non-lambda arg
  void blockLambdaNonLastArg() {
    someMethod(
      item -> {
        process(item);
      },
      "suffix"
    );
    anotherMethod(
      "prefix",
      item -> {
        transform(item);
        save(item);
      },
      "suffix"
    );
  }
}
