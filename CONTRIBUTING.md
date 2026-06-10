# Contributing to jacet

Thanks for your interest. Please read this before opening a pull request.

## Discuss first

**Open an issue before writing code** for anything beyond a trivial bug
fix or typo. This applies to:

- New formatting rules or options
- Changes to existing formatting behavior
- Refactorings that touch multiple modules
- CLI flag additions

jacet is an opinionated formatter in the Prettier tradition: configurability
is explicitly *not* a goal. Many reasonable-sounding requests ("make X
configurable") will be declined on principle, and we would rather say that
in an issue thread than in a PR review after you have written the code.

Bug fixes with a clear reproduction and documentation patches can go
straight to a PR.

## Reporting bugs

Open an issue with:

- The input Java source (minimised — the smallest snippet that reproduces)
- The expected output
- The actual output
- jacet version (`jacet --version` or commit SHA)

## Development setup

Requires a JDK 17 or newer to bootstrap the Gradle wrapper — the build auto-provisions
the JDK 25 toolchain it compiles with. No global Gradle needed.

```bash
# Build everything
./gradlew -p formatter build

# Run core tests
./gradlew -p formatter :jacet-core:test

# Run a single test
./gradlew -p formatter :jacet-core:test --tests "de.irotation.jacet.SnapshotTest"

# Regenerate ANTLR parser after grammar changes
./gradlew -p formatter :jacet-core:generateGrammarSource

# Run the CLI against a file
./gradlew -p formatter :jacet-cli:run --args="--check src/"
```

See `CLAUDE.md` for a deeper tour of the pipeline and module layout.

## Tests

Every change needs tests. In practice this means:

- **New formatting behavior**: add a snapshot fixture under
  `formatter/jacet-core/src/test/resources/snapshots/input/` with the
  expected output under `expected/`. On first run the expected file is
  auto-generated — review it, then commit it.
- **Bug fix**: add a snapshot fixture that reproduces the bug before
  fixing it, so the regression stays covered.
- **Idempotence** (`format(format(x)) == format(x)`) is a core invariant.
  The test suite enforces it; do not weaken that check to get a PR green.

## Code style

- Java source is formatted by jacet itself — run `./gradlew -p formatter
  :jacet-cli:run --args="--write ."` before submitting.
- `@NullMarked` on every package; use `@Nullable` where needed.
- `final` on all parameters and local variables.
- Prefer the existing delegate-formatter structure over adding new
  top-level classes.

## Commit messages

**Required:** [Conventional Commits](https://www.conventionalcommits.org/).

Format:

```
<type>(<scope>): <subject>

<body explaining the why>
```

Types in use: `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `build`,
`ci`, `chore`, `style`, `revert`. Scopes map to modules: `core`, `cli`,
`grammar`, `gradle`, `ide`, `docs`. Root-level changes can omit the scope.

The body should explain *why* the change is needed, not *what* the diff
does — the diff already shows that. A short paragraph is fine; a single
sentence is often enough.

Breaking changes: append `!` before the colon and add a `BREAKING CHANGE:`
footer.

## Pull requests

- One topic per PR. If you find yourself writing "and also" in the
  description, split it.
- Reference the issue: `Closes #N` in the commit or PR body.
- CI must be green before review.
- Keep the diff focused: no unrelated reformatting, no opportunistic
  refactors of code you did not otherwise need to change.
- Expect review cycles. This is a volunteer-maintained project; there is
  no response-time SLA.
