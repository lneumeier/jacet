// assert-istrue-arg-expand
class AssertTests {
  void assertIsTrue(String v) {
    Assert.isTrue(
      StringUtils.isNumeric(v) || "VT".equalsIgnoreCase(v),
      () -> ("Must be numeric or VT: " + v)
    );
  }

  // assert-parens-indent
  void assertParens(Object actualValue) {
    assert (
      Set.of(VALUE_ONE, VALUE_TWO, VALUE_THREE)
        .contains(actualValue)
    ) : "Value not in set";
  }
}
