# Selectable data reset groups

Data Reset uses four user-facing groups instead of exposing storage collections: accounts/transactions/history, categories/rules/labels, stored credentials, and application settings. Resetting categories, rules, and labels also removes category and label references from retained transactions because those references would otherwise point at deleted data. Reset is best-effort and refreshes the running application; backup, restore, and confirmation are intentionally out of scope for the current development-mode feature.
