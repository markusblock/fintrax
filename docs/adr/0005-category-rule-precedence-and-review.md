# Category rules use priority for category and note, then require review before batch save

Status: accepted

Category rules can overlap. Applying them to existing transactions without showing the result makes category replacement and combined label behavior difficult to verify before data changes.

Decision:

- Evaluate enabled rules in ascending priority order.
- The highest-priority matching rule supplies the category and note actions for a transaction.
- Add labels from every matching rule, retaining existing labels and avoiding duplicates.
- Applying one rule from its context menu uses that selected rule's actions only; other enabled matches are shown as overlap warnings.
- Applying rules to existing transactions first produces a review preview. Saving changes occurs only after the user confirms that preview; cancelling leaves transactions unchanged.
- The review defaults to filling missing categories and labels. Disabling that option permits category replacement; notes remain unaffected by the option.
- Transactions matched by rules but receiving no effective changes remain visible in the review summary.

Consequences:

- Overlapping rules can add labels together without allowing lower-priority category or note actions to override the top match.
- Users can inspect proposed changes and conflicts before applying them.
- Hibiscus import and bank sync save source data without applying rules automatically. Their success reports offer scoped review: all transactions after import, successful new transactions after sync.
