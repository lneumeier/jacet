---
name: team-review
description: Multi-role team code review (Architect, Senior Dev, Security, Test Coverage, Project Style) in a fresh sub-agent context. Use when the user asks for a team review, a multi-perspective review, or invokes /team-review — optionally with a git range (e.g. HEAD~3..HEAD) or a path as scope.
---

# Team Review

Invoke the `team-review` sub-agent via the Agent tool (`subagent_type: team-review`) to run a multi-role code review in a fresh, isolated context.

Pass the scope `$ARGUMENTS` to the sub-agent in its prompt. If `$ARGUMENTS` is empty, instruct the agent to default to `git diff HEAD` (all uncommitted + staged changes against the last commit).

When the sub-agent returns, print its report **verbatim** in the chat. Do NOT summarize, reformat, re-prioritize, or filter findings. Do NOT write the report to any file.

After the verbatim report, add exactly one closing line:

`Which items should I fix?`
