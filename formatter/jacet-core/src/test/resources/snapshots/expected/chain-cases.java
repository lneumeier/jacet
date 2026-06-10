class ChainCases {

  // chain-force-expand-non-simple-args: non-simple arg forces full chain expansion
  // >2 calls and a call with a non-simple argument (a `new T(...)` with >2 args) force the whole
  // chain to expand one call per line, even though it would fit on a single line.
  void forceExpand(Object result) {
    assertThat(result)
      .usingRecursiveComparison()
      .isEqualTo(new LoggerDto(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE));
  }

  // >2 calls but every argument is simple (identifier, class literal, simple method reference, a
  // call with <=2 simple args) — the chain stays on one line when it fits.
  void staysSimple(Builder builder, NumberType validator) {
    new NumberBuilder().allowNull().allowNegative().build(mock(NumberType.class), validator);
  }

  java.util.List<Object> simpleMethodRefs(java.util.stream.Stream<Object> s) {
    return s.map(Object.class::cast).map(Foo::bar).distinct().toList();
  }

  // chain-force-wrap-receiver: receiver forces wrap on long fluent chain
  void forceWrapReceiver(Object mock) {
    await()
      .atMost(5, SECONDS)
      .untilAsserted(() -> verify(mock).handleSomething(expectedValue));
  }

  // chain-methodref-args-stays-flat: method-reference args keep chain flat
  void methodRefArgsStaysFlat(java.util.Collection<ExportColumnDto> exportColumns) {
    final java.util.List<Object> headline = exportColumns.stream().map(this::translateColumnTitle).map(Object.class::cast).toList();
  }

  // chain-plus-literal-as-method-arg: chain plus literal as method argument
  // Chain as LHS of binary + inside method arg — chain dots should align under receiver
  void chainInBinopArg() {
    nodeEntity = new NodeEntity(
      nodeService
        .findFirstByOrderByNodeNumberDesc()
        .orElseGet(() -> new NodeEntity(0L))
        .getNodeNumber() + 1
    );
  }

  // Standalone chain in binop — same issue
  void standaloneChainBinop() {
    final long number =
      nodeService
        .findFirstByOrderByNodeNumberDesc()
        .orElseGet(() -> new NodeEntity(0L))
        .getNodeNumber() + 1;
  }

  // chain-segment-comments: comments interleaved between chain segments
  Optional<String> findOrCreate(final long key) {
    return repository
      .find(key)
      // when an existing entry is reused, validate cross-references first
      .filter(entry -> validator.check(entry))
      // otherwise create a fresh one and run the same validation
      .or(() -> Optional.of(factory.build(key)));
  }

  // chain-this-field-head-wraps-first-call: lowercase field head wraps first call
  // `this.someRepository` is NOT a factory head (the field name is lowercase), so the first call
  // wraps to its own line rather than gluing onto `this.someRepository`.
  java.util.List<View> loadAll(NodeFilter nodeFilter, Sort recordSort) {
    return this.distinctProjectionRepository
      .findAll(GenericLayoutNode.class, specification, GenericNodeProjection.FIELD_KEYS, GenericNodeProjection.class, recordSort)
      .stream()
      .map(adapter::toView)
      .toList();
  }

  // A capitalized field name before the first call IS a factory head: `com.example.Stream.of(...)`
  // stays glued; only the following calls wrap.
  java.util.stream.Stream<X> factoryHead() {
    return com.example.Stream.of(firstElementValueHere, secondElementValueHere, thirdElementValueHere)
      .filterElements(predicate)
      .mapElements(mapper);
  }

  // method-chain-wrap: assorted chain-wrapping scenarios
  public void shortChainsStayFlat(final Dto dto, final Adapter recordAdapter) {
    dto.getRecords().stream().map(recordAdapter::toModel).toList();
    list
      .stream()
      .filter(x -> x > 0)
      .map(x -> x * 2)
      .toList();
    Optional.ofNullable(x).map(y).orElse(z);
  }

  public void factoryChainBoundFirstSegment() {
    Optional.ofNullable(x)
      .map(item -> item.getInner().getValue())
      .filter(v -> v != null)
      .orElse(fallback);
  }

  public void fluentChainSplitsReceiver(final Model model) {
    model
      .getChildNode()
      .map(pk -> pk.getId().getChildNumber())
      .orElse("");
  }

  public void thisIsFactory() {
    this.getNode(nodeId)
      .map(node -> updateValue(node))
      .orElse(false);
    this.getNode(nodeId)
      .map(node -> node.updateValue())
      .filter(x -> x.isValid())
      .orElse(false);
  }

  public void simpleArgsStayFlat(final Stream<String> input) {
    Stream.of(enumConstants).map(String::valueOf).toList();
    list.stream().sorted().distinct().toList();
  }

  public void streamWithJoinerStaysFlat(final java.util.Collection<ErrorContainer> list) {
    super(list.stream().map(ErrorContainer::getErrorCode).map(ErrorCode::toString).collect(Collectors.joining(",")));
  }

  // assignment-chain-rhs-breaks-at-dots: assignment RHS chain breaks at dots
  void assignmentChainRhs(ContainerNodeRelations containerNodeRelationGroup, int i) {
    final GenericExternalSourceFetchNodeChildRelationsRowsItem childNodeRelationItems = containerNodeRelationGroup
      .getChildRelationSet()
      .get(i);
  }

  void chained(SequenceNodeRepository sequenceNodeRepository, GroupingId groupingId, String sequenceType) {
    final SequenceNodeEntity sequenceNode = sequenceNodeRepository
      .findById(new SequenceNodeEntityId(groupingId, sequenceType))
      .orElseGet(() -> this.createSequenceNode(groupingId, sequenceType));
  }

  // text-block-receiver-keeps-call-inline: text-block receiver keeps trailing call inline
  // A text block hugs the `=` and a trailing call on it stays inline — the text block's long inner
  // lines must not force the assignment or the call arguments to break.
  void textBlockReceiver() {
    final byte[] content = """
      <?xml version="1.0" encoding="UTF-8"?>
      <Voucher xmlns="urn:oasis:names:specification:ubl:schema:xsd:Voucher-2"/>
      """.getBytes(StandardCharsets.UTF_8);

    // A plain text block (no trailing call) also hugs the `=` rather than wrapping after it.
    final String document = """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
      </w:document>
      """;
  }
}
