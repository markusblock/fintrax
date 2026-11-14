# Triage Labels

Skills may refer to these canonical triage roles. Use the mapped GitHub label only when one exists; don't substitute an issue-type label for a missing status label.

| Canonical role | GitHub label | Meaning |
| --- | --- | --- |
| `needs-triage` | Not configured | Maintainer needs to evaluate this issue. Leave unlabeled until triaged. |
| `needs-info` | Not configured | Waiting on reporter for more information. Ask in a comment; no status label is available. |
| `ready-for-agent` | `ready-for-agent` | Fully specified and ready for an agent. |
| `ready-for-human` | Not configured | Requires human implementation; no status label is available. |
| `wontfix` | `wontfix` | Will not be actioned. |

Check the live repository labels with `gh label list --repo markusblock/fintrax`. If a missing status label is needed, ask a maintainer to add it rather than repurposing `bug`, `enhancement`, `question`, or another issue-type label.
