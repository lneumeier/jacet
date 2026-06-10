class LambdaChainCases {

  // this-receiver-vs-plain: two 2-call chains ending in a block lambda. prettier-java 2.9.7 keeps both
  // chains flat (a 2-call chain stays linear when its first line fits) and hugs the block lambda. jacet matches.
  void thisReceiverVsPlain() {
    this.findByName(name).ifPresent(item -> {
      process(item);
      save(item);
    });
    repository.findById(id).orElseThrow(() -> {
      return new NotFoundException("Not found: " + id);
    });
  }

  // three-calls-wraps: a three-call chain with a block lambda map wraps at the dots
  Optional<UserDto> updatePassword(final String name, final String pw) {
    return this.findByLoginName(name).map(user -> { user.setLastName(""); user.setPassword(pw); return repository.save(user); }).map(this::toDto);
  }

  // expression-lambda-breaks-at-dots: an expression-bodied lambda in a chain breaks at the dots
  DocumentType lookup(Document document) {
    return document.getDocumentType().orElseThrow(() -> new DocumentException("Unknown document type for input file: " + document.getFilename()));
  }

  // expression-lambda-breaks-at-dots: a two-call chain with a block lambda stays inline (hugs)
  void blockLambdaStaysInline(Repository repository, UserId userId) {
    repository.findById(new UserEntityId(userId)).ifPresent(entity -> {
      entity.setEnabled(true);
      repository.saveAndFlush(entity);
    });
  }

  // two-call-block-lambda-hugs: a two-call chain ending in a block lambda hugs `(condition -> {`
  void twoCallBlockLambdaHugs(Action action) {
    action.getConditions().forEach(condition -> {
      this.validate(condition);
      actionEntity.add(condition);
    });
  }

  // chain-body-arg-hangs: a chain whose lambda arg body is itself a chain hangs under the arrow
  java.util.List<Ou> chainBodyArgHangs(java.util.List<Ou> listToBeFiltered, java.util.List<Direct> directOrganizationUnitMembers) {
    return listToBeFiltered.stream().filter(ou -> directOrganizationUnitMembers.stream().map(OrganizationUnitBaseDataModel::getId).anyMatch(id -> ou.getMainOrganizationUnit().getId().equals(id))).toList();
  }

  // chain-body-only-arg-hangs: a single lambda arg whose body is a chain hangs under the arrow
  void chainBodyOnlyArgHangs(java.util.List<Direct> directOrganizationUnitMembers) {
    doSomethingWithThisLongMethodName(ou -> directOrganizationUnitMembers.stream().map(Foo::getId).anyMatch(id -> ou.getOrg().getId().equals(id)));
  }
}
