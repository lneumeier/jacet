class LatentLambda {
  // lambda-arg-expand
  void argExpand(Cache cache, Config config, No no) {
    final List<Range> noRanges = cache.getRanges(no, config, () -> config.getGroupConfigForNestedNodeValue().getValueForChild(no));
  }

  // lambda-arg-extra-indent
  List<Item> argExtraIndent(List<Item> list, Service service) {
    return list
      .stream()
      .filter(item -> !service.isAggregatedValueOverMax(item.entity(), item.combinedValue().getAmount()))
      .toList();
  }

  // lambda-arg-hug
  void argHug(List<Item> list) {
    list
      .stream()
      .filter(Util.distinctByKey(item -> String.join(",", item.getA(), item.getB())))
      .toList();
  }

  // lambda-arg-inline-break
  void argInlineBreak(List<Action> actions, List<Step> steps) {
    actions.removeIf(action -> (!action.isDone() && steps.stream().noneMatch(s -> s.getType() == action.getType())));
  }

  // lambda-arg-placement
  void argPlacement(List<Param> list) {
    list
      .stream()
      .flatMap(param -> // trailing comment
        param.getItems().stream()
      )
      .collect(Collectors.toList());
  }

  // lambda-body-wrap
  List<Range> bodyWrap(Parameter parameter) {
    return parameter
      .getRecords()
      .getSelectedMappedChildItem(IndexedRecordValueViewX::isPickedForGroup, p -> new Range(p.getStartIndexValueX(), p.getEndIndexValueX()));
  }

  // lambda-chain-wrap
  void chainWrap(Service service) {
    assertThatThrownBy(() -> service.execute(param1, param2, param3, param4)).isInstanceOf(SomeException.class);
  }

  // lambda-no-wrap
  void noWrap(List<Item> list, Config config) {
    list
      .stream()
      .filter(x -> x.isValid())
      .findFirst()
      .orElseThrow(() -> new RuntimeException(String.format("Entry '%s' not found in configuration!", config.getOptionName())));
  }

  // lambda-params-in-method-call
  Optional<BigDecimal> paramsInMethodCall(Dto dto) {
    return Optionals.mapIfAllPresent(dto.getFirstRecordValue(), dto.getSecondValue(), (firstRecord, external) ->
      firstRecord.getTallied().add(external)
    );
  }

  // block-lambda-hug
  void blockLambdaHug(Object value) {
    assertThat(value).satisfies(v -> {
      assertThat(v).isNotNull();
      assertThat(v).extracting(Dto::getId).isEqualTo(1L);
    });
  }

  // method-arg-vs-chain-lambda-break
  void methodArgVsChainLambda(Service someService) {
    when(someService.findByVeryLongMethodName(param)).thenAnswer(invocation -> {
      return computeResult(invocation);
    });
  }
}
