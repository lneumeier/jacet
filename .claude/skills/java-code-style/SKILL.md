---
name: java-code-style
description: Project-specific Java conventions for the jacet codebase. Use when generating, modifying, or reviewing any `.java` file in this repo.
user-invocable: false
paths:
  - "**/*.java"
---

# Java Code Generation Rules

These rules apply whenever generating or modifying Java code in this project.

## Null-Safety (@NullMarked)

- All classes use `@NullMarked`. Parameters and return types are non-null by default.
- Only add `@Nullable` where a value is genuinely nullable.
- Never add defensive null checks for values that cannot be null under the type contract (e.g. ANTLR `ParserRuleContext` children that are guarded by a keyword check, or return values from `@NullMarked` methods).
- Never add `@Nullable` to override a method whose parent declares a non-null return type.

## Collections

- Declare collection variables as interface types: `List`, `Set`, `Map` — never `ArrayList`, `HashSet`, `HashMap` in declarations.
- Declare the weakest sufficient type at parameters, fields, and return types — `Iterable` (iterate-only) over `Collection` (needs `add`/`size`/`isEmpty`) over `List` (needs index access). The same applies to ANTLR types: `ParseTree` over `ParserRuleContext`/`ExpressionContext`, `TokenStream` over `CommonTokenStream`, when the wider API is not used.
- When the first thing after creating a list is `addAll()`, use the copy constructor instead: `new ArrayList<>(source)` not `new ArrayList<>(); list.addAll(source);`.
- Use `Comparator.comparingInt()` / `Comparator.comparing()` instead of manual lambda comparators.

## Switch Expressions

- Single-expression switch arms need no braces: `case Foo f -> expr;` not `case Foo f -> { expr; }`.
- Use braces only when the arm contains multiple statements or control flow (`return`, `break`).
- Use unnamed variables (`_`) for pattern bindings where the variable is not referenced: `case Line _ -> ...`.

## No Speculative Code

- Do not add factory methods, constructors, or parameters "for completeness" — only add what is actually called.
- Do not pass parameters through constructors just to forward them if the class itself never reads them.
- Do not create dead variables that are populated but never queried.

## Idiomatic Java

- **Sequenced-collection accessors:** `getFirst()` / `getLast()` / `addFirst()` instead of `get(0)` / `get(size() - 1)` / `add(0, …)`.
- **Constant on the left of `equals`:** `"&&".equals(op)` not `op.equals("&&")`.
- **Method reference over lambda** where one exists: `ImportStatement::isStatic`, not `i -> i.isStatic()`.
- **No redundant `if`/boolean:** `return cond;` not `if (cond) return true; return false;`.
- **Minimal surface:** no wider visibility than needed (drop `public` where package-private suffices), no redundant `@Nullable`, no superfluous explicit type witness (`.map(…)` not `.<Document>map(…)`).

## Comments & Documentation

- **Method bodies are comment-free.** No inline comments inside method bodies — not even for the "why".
- The durable **why** — rationale, rejected alternatives, invariants, jacet-printer quirks (e.g. no break-propagation across group boundaries) — goes into **method or field javadoc**, attached to the contract, never inline.
  - Examples of javadoc-worthy facts: why `ImportSorter` runs *after* the ANTLR pass, why `switchLabeledRule` accepts only `ARROW`, why `PredictionMode.LL` is set, why a `Group` is force-expanded.
  - For line-local algorithm notes inside long `switch` methods (`DocumentPrinter`, `StatementFormatter`), express them as a **per-variant list in the method javadoc**, not as inline comments.
- Never paraphrase the code (`// increment i`, `// initialize the list`). Never write comments that merely assert prettier-java behavior (`// matches prettier-java's X`) — that claim belongs in the snapshot suite, which is the authoritative record of behavior.
- No section-divider comments (`// ── X ──`).
- Behavioral rules are encoded as snapshot fixtures, not prose. A well-named fixture (`hugs-block-lambdas`) is the precise, executable spec. If a behavioral claim has no test, add a fixture *before* deleting the claim.
- Javadoc only on genuinely meaningful API surface (the contract). Do not Javadoc self-evident getters or trivial helpers.
- A wrong comment is worse than none — when you change code, update or delete its javadoc in the same edit.

## `final` Everywhere

- All method parameters and local variables must be `final`.
- Exception: loop variables that must be reassigned.

## Static Imports — Production vs. Test

- In production code (`src/main/java`): use static imports for `Document` factory methods (e.g. `import static de.irotation.jacet.document.Document.text`). These are the only allowed static imports in production code.
- In test code (`src/test/java`): static import assertion methods (`assertEquals`, `assertTrue`, etc.).

## Type Declarations

- No `var` in production code — use explicit types.
- `var` is allowed in test code where it improves readability.
- Records inside a class are fine only if used exclusively within that class. If referenced elsewhere, extract to own file.
