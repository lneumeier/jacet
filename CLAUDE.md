# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Native Java code formatter in the style of Prettier, using the Wadler-Lindig pretty-printing algorithm. No Node.js dependency — pure Java,
targeting GraalVM native image compilation.

Package: `de.irotation.jacet`

## Build & Test

Java 25. Uses Gradle wrapper (`./gradlew`). The project uses Gradle composite builds — each top-level directory is an independent Gradle
project.

```bash
# Build formatter (core + cli)
./gradlew -p formatter build

# Build buildtools (plugin-gradle)
./gradlew -p buildtools build

# Run all tests in core
./gradlew -p formatter :jacet-core:test

# Run a single test class
./gradlew -p formatter :jacet-core:test --tests "de.irotation.jacet.SnapshotTest"

# Run a single test method
./gradlew -p formatter :jacet-core:test --tests "de.irotation.jacet.JacetFormatterTest.formatsWithCustomOptions"

# Force re-run (skip up-to-date checks)
./gradlew -p formatter :jacet-core:test --rerun

# Regenerate ANTLR parser after grammar changes
./gradlew -p formatter :jacet-core:generateGrammarSource

# Run CLI (working dir = project root)
./gradlew -p formatter :jacet-cli:run --args="--check src/"
./gradlew -p formatter :jacet-cli:run --args="--write src/"
echo 'code' | ./gradlew -p formatter -q :jacet-cli:run --args="--stdin-filepath Foo.java"
```

## Module Structure

```
formatter/             → Composite build: the formatter product
  jacet-core           → ANTLR4 grammar (JavaLexer.g4, JavaParser.g4) + formatting engine
  jacet-cli            → Picocli CLI (depends on jacet-core), GraalVM native image
buildtools/            → Composite build: build tool plugins (jacet-core via Maven coordinate, substituted by includeBuild)
  plugin-gradle        → Gradle plugin
ide/                   → IDE integrations (planned, currently empty)
```

## Formatting Pipeline

```
Source String
  → ANTLR4 Lexer/Parser → CST (with comments on hidden channel)
  → DocumentVisitor (ANTLR visitor) → Document IR tree
  → DocumentPrinter (Wadler-Lindig) → formatted String
  → ImportSorter (post-processing) → final output
```

Parse errors → source returned unchanged (never produce unpredictable output).

**Key classes** (algorithm details live in the class javadoc):

- `JacetFormatter` — entry point, orchestrates the pipeline
- Visitor layer: `DocumentVisitor` dispatches CST nodes via `HandlerRegistry` to delegate formatters, which implement `HandlerProvider`
  and receive a `FormattingDispatch`; `DocumentVisitorFactory` wires it all together
- Comments: `CommentAttacher` (pre-pass attaching hidden-channel comments as leading/trailing/dangling) + `CommentHelper` (rendering,
  double-emission guard)
- `Document` — sealed IR interface; record nodes live in the `document` package (Text, Line, Group, Indent, IfBreak, …), static
  factories on `Document` itself
- `DocumentPrinter` — stack-based Wadler-Lindig algorithm (FLAT vs BREAK mode, `fits()` check)
- `ImportSorter` — runs after ANTLR formatting to preserve blank lines between groups

**Delegate formatters** (each handles a focused area, accessed via `FormattingDispatch`):

- `CompilationUnitFormatter` — package, imports, top-level structure
- `DeclarationFormatter` — type declaration headers (class, interface, enum, record, annotation type)
- `ClassBodyFormatter` — class/interface/record bodies, member and enum-body declarations, blank-line preservation between members
- `MethodFormatter` — methods, constructors, parameters
- `StatementFormatter` — blocks, variables, simple statements (return, throw, break, continue, yield, assert)
- `ControlFlowFormatter` — if/else, for, enhanced-for, while, do-while, synchronized
- `SwitchFormatter` — switch statements and switch expressions, switch labels, switch rules
- `ExceptionFormatter` — try, catch, finally, try-with-resources
- `OperatorFormatter` — operator expressions (binary/assignment, ternary, unary, instanceof, cast, indexing), parenthesized primaries
- `InvocationFormatter` — method calls and member-access chains (prettier-style chain layout), argument lists, method references
- `CreatorFormatter` — object and array creators (`new T(...)`, `new int[]{...}`), diamond / type-argument forms
- `LambdaFormatter` — lambda expressions: parameters and block/expression bodies
- `TypeFormatter` — type references, generics, annotations
- `LeafFormatter` — modifiers, identifiers, literals, pattern matching
- `ModuleFormatter` — Java module declarations (module-info.java)

**Shared helpers:**

- `Operators` — operator-text reconstruction, operator classification, leaf-like-RHS tests, and the ternary RHS layout reused by
  `StatementFormatter`
- `Modifiers` — formats modifier lists (declaration and variable modifiers, annotations)
- `Tokens` — emission markers that source text from the token stream with provenance for the coverage guarantee
  (`sourced`/`removed`/`joinSourced`)
- `CoverageVerifier` — verifies every input token is emitted exactly once (the unconditional token-coverage guarantee)
- `FormatterDirectives` — extracts and restores `@formatter:off`/`@formatter:on` regions around the formatting pass

## Testing

- **Snapshot tests** (`SnapshotTest`): input files in `src/test/resources/snapshots/input/`, expected output in `expected/`. Missing
  expected files are auto-generated on first run — review and re-run.
- **Dogfooding test** (`DogfoodingTest`): formats the project's own source files, checks idempotence.
- **Idempotence**: `format(format(x)) == format(x)` — this is a core invariant tested everywhere.

## Working Style

**On formatting changes:**

- jacet targets its own curated formatting spec — the snapshot fixtures and `expected/`
  files are the source of truth, not any external formatter.
- Snapshot-first: reproduce any desired change as a minimal Java pattern in a snapshot
  fixture (`jacet-core/src/test/resources/snapshots/input/`) with the expected output
  before changing formatter code.
- Locate the cause in jacet code first — which delegate formatter, which rule, which
  `Document` IR node produces the output. Don't infer the cause from the output alone.
- Preserve idempotence: `format(format(x)) == format(x)`. Run the snapshot and
  dogfooding tests after any change.

**On decision points:**

- If multiple plausible solutions exist, or new sub-problems are discovered mid-task:
  stop and ask. Don't decide unilaterally and announce.

## Key Conventions

Full Java code-style rules (null-safety, collections, switch arms, comments/javadoc, `final`, static imports, `var` policy) live in the
`java-code-style` skill (`.claude/skills/java-code-style/`), auto-applied when editing any `.java` file. The conventions below are the
jacet-specific architectural rules that go beyond pure code style.

- **Import sorting runs after ANTLR formatting** — this ordering is intentional so blank lines between import groups survive the ANTLR pass.
- **`ImportSorter.parse()` only parses the preamble** — stops at the first non-import/non-blank line to avoid picking up imports from string
  literals and text blocks.
- **`forceBraces` (default: true)**: wraps single-statement bodies (if/for/while) in `{}`. Else-if chains are not double-wrapped.
- **Grammar modification**: `switchLabeledRule` only accepts `ARROW` (not `COLON`) so ANTLR correctly disambiguates arrow-style vs
  colon-style switch. `PredictionMode.LL` is also set.
- **Trailing comments** use `LineSuffix` in the Document IR. `CommentHelper.leadingComments()` skips same-line trailing comments to prevent
  double-emission.
- **Token-coverage verification runs on every format**: `CoverageVerifier` checks the printed document *before* import post-processing. If
  the output would drop or duplicate an input token, `JacetFormatter` returns the original source unchanged and reports it as a verification
  error in `FormatResult` (the CLI and Gradle tasks warn and skip the file). There is no opt-out flag — predictable output over best-effort.
  The one deliberate downstream exception: with `imports.removeUnused` enabled, the string-based import pass drops unused imports after the
  check has passed.

## Configuration

`FormatterOptions` record: `printWidth` (140), `tabWidth` (2), `useTabs` (false), `forceBraces` (true), `endOfLine` (LF), `imports` (groups:
java, javax, jakarta, org, com, de, lombok; static: TOP; removeUnused: true).

Config file: `.jacet.json`, lookup walks up from the project dir and stops at the repository root (the first directory containing a
`.git` entry — directory or file); outside a repository it walks to the filesystem root. There is no home-directory fallback: nothing
outside the repository is ever picked up, so the same tree formats identically on every machine (use `--config`/`configFile` for a file
from elsewhere). Parsed with a custom JSON extractor (no external JSON library). `ConfigLoader.locate()` exposes the resolved file path
(without parsing) for callers that must declare it as a build input.

Both frontends layer overrides over the config file, highest priority last: defaults < `.jacet.json` < frontend overrides.

- **CLI**: flags override the loaded config (`OptionsResolver`); negatable booleans (`--no-use-tabs`) exist to flip a config-enabled
  setting back per invocation. `--config <path>` forces a specific file; `--no-config` ignores config files entirely.
- **Gradle plugin**: the `jacet { }` DSL block overrides the walk-up `.jacet.json` (`JacetExtension.applyOverrides`). The located config
  file is wired as an `@InputFile` so editing `.jacet.json` invalidates `formatJava`/`checkFormatJava`. `jacet { configFile = ... }` forces a
  specific file (counterpart to CLI `--config`); there is no `--no-config` counterpart.
