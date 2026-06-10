class LatentMethodChain {
  // method-chain-indent
  void indent(Service service) {
    x = new Foo(
      service
        .findFirst()
        .orElseGet(() -> new Foo(0L))
        .getValue() + 1
    );
  }

  // method-chain-indent-ternary
  Object indentTernary(boolean condition, Service service) {
    return condition
      ? value1
      : service
          .getResult(id)
          .map(Fn::convert)
          .orElseThrow(() -> new Ex());
  }

  // method-chain-in-call-arg
  Dto inCallArg(boolean condition, Adapter adapter, Service service) {
    final Dto dto = condition ? adapter.toView(service.get(id).orElseThrow(() -> new Ex())) : null;
  }

  // method-chain-super-wrap
  Set<Model> superWrap(UUID id) {
    return super.getRelatedEntitiesCollectionForRecord(id)
      .stream()
      .map(e -> new Model(e))
      .collect(Collectors.toSet());
  }

  // method-chain-unnecessary-break
  FirstSecondAmount unnecessaryBreak(Record record, BigDecimal secondValue) {
    return new FirstSecondAmount(
      record.getRecordsAmount().subtract(secondValue), // first
      secondValue // second
    );
  }

  // field-access-chain-break
  List<View> fieldAccessChain(EntryGroup entryGroup, Adapter adapter) {
    return entryGroup.childEntries.stream().map(adapter::toView).toList();
  }

  // call-args-indent
  void callArgsIndent(Service someService) {
    someService.findByName(firstName, lastName).stream().collect(Collectors.toList());
  }

  // chain-after-broken-args
  void chainAfterBrokenArgs(Service someService) {
    someService
      .createEntity(paramOne, paramTwo, paramThree)
      .chainedMethod(veryLongArgOne, veryLongArgTwo, veryLongArgThree, veryLongArgFour);
  }

  // chained-call-arg-indent
  void chainedCallArgIndent() {
    this.getRelatedNodeChildValueGroupItems(someArg.getChildNodeItem(), someArg.getId().getGroupLevelId())
      .filter(x -> x.isPlannable())
      .ifPresent(x -> process(x));
  }

  // method-chain-wrap (trailing comment disrupts fits)
  void wrapTrailingComment(PersonService personService, UserId userId, Person person) {
    boolean result = (personService.getAuthorizedPerson(userId, person.getId()).isEmpty() && other.check()); // comment
  }
}
