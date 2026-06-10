class LatentTernary {
  // ternary-branch-comment-drop
  List<Id> getIds(Selection sel, Filter<?> filter, AuthType auth) {
    final Stream<Id> ids = sel.isSelectAllMode()
      ? // all items matching filter
        this.getFiltered(sel, filter, auth)
      : // only items in include list
        this.getIncluded(sel, new Filter<>(), auth);
    return ids.toList();
  }

  // ternary-else-comment-drop
  Object elseComment(Type type, Filter filter, Form form, Db db) {
    return type == Type.NEW
      ? filter.getNewFields(form)
      : // removes unchanged fields
        filter.getChangedFields(form, db);
  }

  // ternary-over-break
  void overBreak(ChronoUnit chronoUnit, boolean isFinite, Object start) {
    ChronoUnit temporalStep =
      chronoUnit != null ? chronoUnit : (isFinite && start instanceof Temporal t ? getDefault(t) : null);
  }

  // ternary-chain-indent
  String chainIndent(boolean condition, Object someObject, Object otherObject) {
    return condition
      ? someObject
          .getFirst()
          .getSecond()
      : otherObject
          .getThird()
          .getFourth();
  }

  // ternary-branch-over-indent
  List<Type> branchOverIndent(String type) {
    return "SPECIAL".equals(type)
      ? List.of(Type.A)
      : List.of(
          Type.B,
          Type.C,
          Type.D
        );
  }

  // ternary-method-args-over-indent
  Outcome methodArgsOverIndent(Entry entry) {
    return entry.getEntryFieldType() == null
      ? EntryResultGroup.reportEntryError(
          NodeField.ENTRY_FIELD_TYPE,
          NodeErrorCode.ENTRY_FIELD_KEY_REQUIRED
        )
      : EntryResultGroup.ok();
  }

  // ternary-continuation-overindent
  Optional<Error> continuationOverindent(boolean cond, Object param1, Object param2) {
    return cond
      ? Optional.empty()
      : Optional.of(
          new NodeError(
            param1,
            param2
          )
        );
  }

  // ternary-wrap
  String wrap(boolean someConditionThatIsTrue, Object someObject, String shortValue) {
    return someConditionThatIsTrue
      ? shortValue
      : someObject.getMethodWithLongName(parameterOne, parameterTwo, parameterThree);
  }
}
