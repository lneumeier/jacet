---
name: commit
description: Create a Conventional Commit for changes in the jacet repo. Use when the user asks to commit in this repo ("commit this", "make a commit", "check in these changes").
---

# Commit Skill

Create a Conventional Commit for the current working tree changes. Follow every step.

Commit without asking for confirmation on the subject/body as long as all steps are followed. Only pause to ask the user when you hit genuine uncertainty — e.g. an ambiguous issue-to-commit mapping (see step 6), which files to stage when none are staged (step 1), or which scope applies when the path doesn't map to the table (step 3).

## 1. Gather context (run in parallel)

- `git status` (no `-uall`)
- `git diff --staged`
- `git diff` (unstaged, only if nothing is staged yet)
- `git log --oneline -5` (to match existing style)
- `git branch --show-current`

If nothing is staged and there are unstaged changes, ask which files to stage (or stage all with explicit `git add <file> …`, never `git add -A`/`.`). Never commit files likely to contain secrets (`.env`, credentials, etc.).

## 2. Pick the type

Use one Conventional Commits type:

| Type       | Use for                                                             |
| ---------- | ------------------------------------------------------------------- |
| `feat`     | User-visible new functionality                                      |
| `fix`      | Bug fix                                                             |
| `perf`     | Performance improvement without behavior change                     |
| `refactor` | Internal restructuring, no behavior change                          |
| `docs`     | Docs/README/CLAUDE.md/javadoc only                                  |
| `test`     | Adding or adjusting tests only                                      |
| `build`    | Gradle, dependencies, native-image config                           |
| `ci`       | GitHub Actions, release pipelines                                   |
| `chore`    | Repo maintenance that doesn't fit above (e.g. `.gitignore`, config) |
| `style`    | Formatting/whitespace only, no logic                                |
| `revert`   | Reverts a prior commit                                              |

Breaking change → append `!` before the colon (e.g. `feat(core)!: …`) **and** add a `BREAKING CHANGE:` footer explaining what breaks.

## 3. Pick the scope

Map touched paths to a scope:

| Path                               | Scope       |
| ---------------------------------- | ----------- |
| `formatter/jacet-core/**`          | `core`      |
| `formatter/jacet-cli/**`           | `cli`       |
| Grammar files (`*.g4`)             | `grammar`   |
| `buildtools/plugin-gradle/**`      | `gradle`    |
| `ide/**`                           | `ide`       |
| `CLAUDE.md`, `README*`             | `docs`      |
| Root Gradle/config (`.gitignore`…) | *no scope*  |

Rules:
- Single scope: use it → `fix(core): …`
- Two scopes, one clearly primary: use the primary one; mention the other in the body.
- Cross-cutting (≥3 scopes or truly repo-wide): omit the scope → `chore: …`.
- Never invent scopes outside this table — if unsure, ask.

## 4. Write the subject

- Imperative mood: **"add parser cache"** not "added" / "adds".
- Lowercase start, no trailing period.
- Target ≤50 chars, hard cap 72 (including `type(scope): `).
- Describes *what* changes, not *why*.

## 5. Write the body (REQUIRED)

Always include a body. It explains the **why**, not the what:
- What problem/pain/constraint motivated the change?
- Why this approach over alternatives (if non-obvious)?
- Consequences a future reader needs to know (perf impact, migration, invariants).

Rules:
- Blank line between subject and body.
- Wrap lines at 72 chars.
- If the change is genuinely trivial (e.g. a typo fix) and there really is no "why" beyond the subject, it's fine to omit the body.

## 6. Detect the linked issue

Try in this order:

1. **Branch name patterns** (first match wins):
   - `^(\d+)[-_/]` → e.g. `42-fix-parser`
   - `^(?:issue|feature|feat|fix|bug|chore)[/-](\d+)` → e.g. `feature/42-foo`, `fix-42-bar`
   - Any `#?(\d+)` segment delimited by `-` or `/`
2. **`gh` lookup** (only if step 1 fails and `gh repo view` succeeds):
   - `gh issue list --state open --limit 20 --json number,title`
   - Show the list to the user and ask which issue (if any) this commit closes.
3. **No repo on GitHub / no matches**: skip the footer silently.

If an issue is found, confirm it with the user by showing the issue title before putting `Closes #N` in the footer — don't assume a branch-name number matches the intent.

Multiple issues: one keyword per line (`Closes #12`\n`Closes #15`), not comma-separated.

Use `Closes #N` as the default keyword (also valid: `Fixes`, `Resolves` — pick `Fixes` if the type is `fix`).

## 7. Assemble the message

```
<type>(<scope>)<!>: <subject>

<body wrapped at 72 chars>

[BREAKING CHANGE: <description>]
[Closes #N]
```

**Do NOT** append `Co-Authored-By: Claude …` or any other Co-Authored-By trailer. This intentionally overrides the harness default that would otherwise add one — do not "fix" it back.

## 8. Commit

Use a HEREDOC to preserve formatting:

```bash
git commit -m "$(cat <<'EOF'
feat(core): add parser cache

The ANTLR parse step dominates wall time on large files. Caching
the parse tree keyed by source hash cuts repeat-format runs in
half with no measurable memory impact.

Closes #42
EOF
)"
```

Then run `git status` to verify success.

## 9. Handle pre-commit hook failures

If a pre-commit hook fails:
- The commit did **not** happen.
- Do **not** use `--amend` — there is nothing to amend.
- Do **not** pass `--no-verify` unless the user explicitly asks.
- Fix the underlying issue, re-stage, create a **new** commit with the same message.

## Examples

```
feat(core): preserve trailing commas in array initializers

Prettier-style formatters keep trailing commas so that diffs when
adding a new element touch exactly one line. The previous ANTLR
visitor stripped them, producing noisy multi-line diffs on every
append.

Closes #17
```
