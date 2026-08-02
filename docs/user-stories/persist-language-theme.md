# User Story: Persist Language and Theme

As a Fintrax user, I want the Language and Theme Save buttons to persist and apply my choices, so my preferences survive application restarts.

## Acceptance Criteria

- Saving Language persists the selected language.
- Saving Theme persists the selected theme.
- Both changes apply immediately without restarting Fintrax.
- Saved values load during application startup.
- Missing or invalid values fall back to defaults.
- This story is separate from Data Reset; Data Reset currently clears only already-persisted settings.
