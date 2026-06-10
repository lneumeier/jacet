class LatentExpressions {
  // paren-group-expand
  boolean parenGroupExpand(Object a, boolean typePresent, Object value, boolean anyMatch) {
    return (
      a == null &&
      (typePresent ||
        (value == null && anyMatch))
    );
  }

  // boolean-group-paren-placement
  boolean booleanGroupParen(A a, B b, D d) {
    return (
      a.isX() &&
      (
        b == Type.A ||
        b == Type.B ||
        b == Type.C
      ) &&
      d.check()
    );
  }

  // group-parens-expansion
  boolean groupParensExpansion(Ruleset ruleset, Object value) {
    return (
      ruleset.getExcludeRules().stream().noneMatch(r -> r.isInSegment(value)) &&
      (
        ruleset.getIncludeRules().isEmpty() ||
        ruleset.getIncludeRules().stream().anyMatch(r -> r.isInSegment(value))
      )
    );
  }

  // parens-expr-indent
  void parensExprIndent() {
    boolean result =
      (
        SomeClass.getFilter(
          argumentOne,
          argumentTwo)
      );
  }

  // binop-continuation-indent
  boolean binopContinuationIndent(List<Item> list) {
    return list.stream()
        .map(Item::getId)
        .distinct()
        .toList()
        .size() >
      1;
  }

  // binop-rhs-indent
  boolean binopRhsIndent(Object obj, Object other) {
    return Objects.equals(
      obj
        .getField()
        .map(x -> x.compareTo(other))
        .orElse(-1) ==
      0
    );
  }
}
