package de.irotation.jacet.parser;

import java.util.List;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;

/**
 * Required superclass for the generated {@link JavaParser}.
 *
 * <p>The method names {@code isValidVarargsPosition()} and {@code isNotIdentifierAssign()}
 * are dictated by the ANTLR4 grammar file (semantic predicates) and must not be renamed.
 *
 * <p>Sourced from <a href="https://github.com/antlr/grammars-v4">grammars-v4</a>.
 */
public abstract class JavaParserBase extends Parser {

  protected JavaParserBase(final TokenStream input) {
    super(input);
  }

  /**
   * Semantic predicate (name fixed by the grammar): varargs ({@code ...}) may appear only on the last
   * component of a record header, so an {@code ELLIPSIS} on any earlier component is rejected.
   */
  public boolean isValidVarargsPosition() {
    final ParserRuleContext parserRuleContext = this.getContext();
    if (!(parserRuleContext instanceof final JavaParser.RecordComponentListContext recordComponentListContext)) {
      return true;
    }

    final List<JavaParser.RecordComponentContext> components = recordComponentListContext.recordComponent();
    return components
      .stream()
      .limit(Math.max(0, components.size() - 1))
      .noneMatch(c -> c.ELLIPSIS() != null);
  }

  public boolean isNotIdentifierAssign() {
    final int la = this._input.LA(1);
    return switch (la) {
      case
        JavaParser.IDENTIFIER,
        JavaParser.MODULE,
        JavaParser.OPEN,
        JavaParser.REQUIRES,
        JavaParser.EXPORTS,
        JavaParser.OPENS,
        JavaParser.TO,
        JavaParser.USES,
        JavaParser.PROVIDES,
        JavaParser.WHEN,
        JavaParser.WITH,
        JavaParser.TRANSITIVE,
        JavaParser.YIELD,
        JavaParser.SEALED,
        JavaParser.PERMITS,
        JavaParser.RECORD,
        JavaParser.VAR -> this._input.LA(2) != JavaParser.ASSIGN;
      default -> true;
    };
  }
}
