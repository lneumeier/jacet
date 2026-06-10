---
name: snapshot-first
description: The required workflow for any change to jacet's formatting output. Use when adding or changing formatting behavior — new Java construct support, altered spacing/wrapping/breaking, or fixing a misformat. NOT for pure refactors, build, or docs changes.
argument-hint: [brief description of the formatting change]
---

# Snapshot-First Formatting Changes

jacet targets its **own curated spec** — the snapshot fixtures and `expected/` files are the source of truth, not any external formatter. Never change formatter code before the desired output exists as a fixture. Follow these steps in order.

## 1. Reproduce as a minimal fixture first

- Write the smallest Java input that exhibits the case in `jacet-core/src/test/resources/snapshots/input/`. Add it to an existing thematic case file if one fits; create a new one only for a genuinely new theme.
- Author the **expected** output by hand in the matching `expected/` path — this is the spec you are committing to. Do not let a misformatting auto-generate the expected file and call it correct.
- Name the fixture for the behavior it pins (`hugs-block-lambdas`), not the bug number. A well-named fixture is the executable spec.

## 2. Locate the cause in jacet code

Find *which* code produces the current output before changing anything — do not infer the cause from the output alone:

- Which **delegate formatter** owns this construct (see CLAUDE.md "Delegate formatters")?
- Which handler / `ParserRuleContext` it dispatches on.
- Which `Document` IR node (`Group`, `Indent`, `Line`, `IfBreak`, `Fill`, …) shapes the layout, and whether the fix belongs at the delegate level or in `DocumentPrinter`.

## 3. Make the change

Apply the fix at the layer identified in step 2. Follow the `java-code-style` rules. Keep the change scoped to the located cause.

## 4. Verify — both suites, plus idempotence

```bash
./gradlew -p formatter :jacet-core:test --tests "de.irotation.jacet.SnapshotTest"
./gradlew -p formatter :jacet-core:test --tests "de.irotation.jacet.DogfoodingTest"
```

- The new fixture must pass with the **hand-authored** expected output unchanged.
- `DogfoodingTest` must stay green — the change must not regress formatting of jacet's own source.
- **Idempotence is the core invariant: `format(format(x)) == format(x)`.** If a new `Document` IR node or handler could break it, ensure a fixture covers the second-pass case.

## 5. If a decision point appears

If multiple plausible spec choices exist, or the change uncovers a new sub-case, **stop and ask** — do not pick a spec unilaterally and announce it. The spec is curated, not derived.
