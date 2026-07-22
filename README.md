# Jacet

A native Java code formatter in the style of [Prettier](https://prettier.io/), using the Wadler-Lindig pretty-printing algorithm. No Node.js dependency — pure Java, compiled to a native binary via GraalVM.

Jacet follows its own curated formatting spec: the output is close to, but not byte-identical with, [prettier-java](https://github.com/jhipster/prettier-java) — the few deviations are deliberate.

## Installation

### Install script (recommended)

The install scripts pick the right native binary for your platform, verify its SHA256 checksum, install it to `$HOME/.jacet/bin` (override with the `JACET_INSTALL_DIR` env var), and add that directory to your `PATH`.

**macOS / Linux:**

```bash
curl -fsSL https://raw.githubusercontent.com/lneumeier/jacet/main/install.sh | bash
```
To pin a version:
```bash
curl -fsSL https://raw.githubusercontent.com/lneumeier/jacet/main/install.sh | bash -s -- v0.3.0
```

**Windows (PowerShell):**

```powershell
irm https://raw.githubusercontent.com/lneumeier/jacet/main/install.ps1 | iex
```
To pin a version:
```powershell
& ([scriptblock]::Create((irm https://raw.githubusercontent.com/lneumeier/jacet/main/install.ps1))) v0.3.0
```

### Manual binary download

Download the latest binary for your platform from [GitHub Releases](https://github.com/lneumeier/jacet/releases):

| Platform       | Binary                   |
|----------------|--------------------------|
| Linux x64      | `jacet-linux-amd64`      |
| Linux ARM64    | `jacet-linux-arm64`      |
| macOS x64      | `jacet-macos-amd64`      |
| macOS ARM64    | `jacet-macos-arm64`      |
| Windows x64    | `jacet-windows-amd64.exe`|

```bash
# macOS / Linux — move onto any directory on your PATH, e.g. /usr/local/bin
chmod +x jacet-*
sudo mv jacet-* /usr/local/bin/jacet
```

### Gradle plugin

```groovy
plugins {
    id 'de.irotation.jacet' version '0.3.0'
}
```

## Usage

### CLI

```bash
# Format files in-place
jacet --write src/

# Check formatting (exit code 1 if unformatted)
jacet --check src/

# Format only the files you changed in the working tree (modified + new), in place
jacet --changed --write

# Format stdin (for editor integration)
echo 'class Foo { void bar() {} }' | jacet --stdin-filepath Foo.java

# Override options
jacet --write --print-width 100 --tab-width 4 src/
```

All CLI options:

```
--write                       Edit files in-place (the default)
--check                       Check formatting without writing
--staged                      Format git-staged files and re-stage them (for pre-commit hooks)
--changed                     Format files changed in the working tree (modified + untracked), in place
--stdin-filepath PATH         Format stdin, use PATH for config lookup
--print-width N               Line width (default: 140)
--tab-width N                 Indentation width (default: 2)
--[no-]use-tabs               Indent with tabs instead of spaces
--end-of-line MODE            lf, crlf, cr, auto (default: lf)
--[no-]force-braces           Add braces to single-statement blocks (default: on)
--static-imports POS          top, bottom, mixed (default: top)
--import-groups LIST          Comma-separated prefixes (default: java,javax,jakarta,org,com,de,lombok)
--[no-]remove-unused-imports  Remove imports never referenced in code or javadoc tags (default: on)
--config PATH                 Path to .jacet.json
--no-config                   Ignore config files
```

### Git pre-commit hook

`--staged` formats the Java files staged in git and re-stages the formatted result, so a pre-commit hook is a one-liner. Create `.git/hooks/pre-commit` and make it executable (`chmod +x .git/hooks/pre-commit`):

```sh
#!/bin/sh
exec jacet --staged --write
```

Every `git commit` then formats the staged Java files automatically. Notes:

- Requires `git` on the `PATH` — git invokes the hook, so it is always present there.
- Skip it for a single commit with `git commit --no-verify`.
- Partially-staged files (staged *and* carrying further unstaged edits) are left untouched, so the hook never pulls unintended changes into the commit — format those manually.
- For a non-modifying gate (pre-push or CI), use `jacet --staged --check`, which exits `1` when a staged file is unformatted.

### Gradle plugin

The plugin picks up `.jacet.json` automatically (same lookup as the CLI, see [Configuration](#configuration)); editing the file invalidates the format tasks. Properties set in the `jacet { }` block override the config file:

```groovy
jacet {
    configFile = file("config/.jacet.json")  // optional: skip the lookup, use this file
    printWidth = 140
    tabWidth = 2
    useTabs = false
    forceBraces = true
    endOfLine = "lf"                         // lf, crlf, cr, auto
    staticImports = "top"                    // top, bottom, mixed
    importGroups = ["java", "javax", "jakarta", "org", "com", "de", "lombok"]
    removeUnusedImports = true               // drop imports never referenced
}
```

Tasks:

```bash
# Format all Java sources
./gradlew formatJava

# Check formatting (CI)
./gradlew checkFormatJava
```

## Configuration

Jacet looks for a `.jacet.json` file, walking up from the target directory and stopping at the repository root (the first directory containing a `.git` entry). Outside a repository it walks up to the filesystem root. The first file found wins. There is no home-directory fallback — nothing outside the repository is ever picked up, so the same tree formats identically on every machine. Use `--config <path>` (CLI) or `configFile` (Gradle) to force a file from elsewhere, or `--no-config` to ignore config files entirely.

```json
{
  "printWidth": 140,
  "tabWidth": 2,
  "useTabs": false,
  "forceBraces": true,
  "endOfLine": "lf",
  "imports": {
    "staticPosition": "top",
    "groups": ["java", "javax", "jakarta", "org", "com", "de", "lombok"],
    "removeUnused": true
  }
}
```

| Option             | Default                              | Description                                       |
|--------------------|--------------------------------------|---------------------------------------------------|
| `printWidth`       | `140`                                | Target line width                                 |
| `tabWidth`         | `2`                                  | Spaces per indentation level                      |
| `useTabs`          | `false`                              | Use tabs instead of spaces                        |
| `forceBraces`      | `true`                               | Wrap single-statement bodies in `{}`              |
| `endOfLine`        | `"lf"`                               | Line endings: `lf`, `crlf`, `cr`, `auto`          |
| `imports.staticPosition` | `"top"`                        | Where static imports go: `top`, `bottom`, `mixed` |
| `imports.groups`   | `["java","javax","jakarta","org","com","de", "lombok"]` | Import grouping prefixes (blank line between groups) |
| `imports.removeUnused` | `true`                           | Remove imports never referenced in code or javadoc tags; wildcard imports are always kept |

All options are optional — omitted values use defaults. CLI flags override config file values.

`removeUnused` detection is token-based (like Spotless / google-java-format): an import survives when its simple name appears as an identifier anywhere outside the import declarations or inside a javadoc reference tag (`{@link}`, `{@linkplain}`, `{@value}`, `@see`, `@throws`). Detection errs toward keeping — fully-qualified usage, usage inside `@formatter:off` regions, and overloaded static members all keep their imports; plain comment prose does not.

## Predictable output

Jacet parses your source with a full ANTLR4 Java grammar and re-prints it with a Wadler-Lindig pretty-printer. Three mechanisms keep the output predictable:

- **Parse errors return the source unchanged** — Jacet never emits a best-effort guess.
- **Token coverage is verified on every run**: if the formatted output would drop or duplicate any input token, the file is returned unchanged and reported as a verification error instead. There is no opt-out. The one deliberate exception: unused-import removal (`removeUnused`, on by default) drops unused imports after this check has passed.
- **Formatting is idempotent**: `format(format(x)) == format(x)` — running Jacet twice never produces a different result than running it once.

Regions between a comment containing `@formatter:off` and the matching `@formatter:on` are preserved verbatim.

## Building from source

Requires Java 25. The repository consists of two independent Gradle composite builds: `formatter/` (core + CLI) and `buildtools/` (the Gradle plugin) — hence the `-p` switch.

```bash
# Build and test the formatter (core + CLI)
./gradlew -p formatter build

# Build and test the Gradle plugin
./gradlew -p buildtools build

# Build native image (requires GraalVM)
./gradlew -p formatter :jacet-cli:nativeCompile

# Run via Gradle
./gradlew -p formatter :jacet-cli:run --args="--check src/"
```

## License

[MIT](LICENSE)

Jacet builds on [ANTLR 4](https://github.com/antlr/antlr4), a Java grammar derived from
[antlr/grammars-v4](https://github.com/antlr/grammars-v4), and [picocli](https://github.com/remkop/picocli) —
see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for their licenses.
