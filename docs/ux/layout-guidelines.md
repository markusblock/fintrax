# Layout guidelines

Use these rules for new screens, panels, dialogs, popups, and actions. Keep layouts clear, resizable, and consistent across light and dark themes.

## Structure

- Screen: header, primary work area, optional persistent footer. Keep one primary task per screen.
- Header: title, optional short context, actions aligned at the trailing edge.
- Panel: optional heading and actions, content, optional footer. Use panels to group related tasks; avoid decorative nesting.
- Place secondary details in a supporting or collapsible area. Keep fixed headers and footers outside scrolling content.
- Include empty, loading, and error states where relevant; each state should explain the next action.

## Foundations

### Spacing

Use a 4 px base. Choose from this scale; do not add one-off values.

| Spacing | Use |
| ---: | --- |
| 4 px | Tight inline separation |
| 8 px | Related controls |
| 12 px | Form rows and grouped controls |
| 16 px | Sections within a panel |
| 20 px | Panel inset |
| 24 px | Screen inset |
| 40 px | Empty-state breathing room |

### Type and surfaces

- Type sizes: 12 px supporting text, 13 px body, 16 px section heading, 24 px screen title. Use weight and size for hierarchy; keep essential instructions at body size or larger.
- Corner radii: 4 px for compact controls, 8 px for panels, full rounding only for badges/chips. Use thin borders to define surfaces; reserve soft shadows for raised surfaces.
- Surface roles: default for the screen, subtle for panels, default for controls. Text roles: default, muted, emphasis. Keep muted text for secondary content.

### Color roles

Use semantic theme colors, never fixed colors for interface meaning.

| Role | Use |
| --- | --- |
| Default / subtle surface | Screen / grouped panel backgrounds |
| Default / muted / emphasis text | Main content / secondary copy / titles and primary values |
| Default border | Panel edges, separators, control boundaries |
| Accent emphasis / subtle / muted | Primary action / selected state / hover or divider emphasis |
| Success / warning / danger | Status, validation, and destructive actions |
| Selection background / foreground | Selected row or item / high-contrast selected text |
| Neutral hover / focus / soft shadow | Hover feedback / keyboard focus / raised surfaces |

Use subtle status fills with matching status text. Pair status colors with text or an icon; keep selection and keyboard focus distinguishable in both themes.

## Component rules

### Screens and panels

- Let the primary work area take remaining space. Keep header and footer content stable while the body grows or scrolls.
- Use side-by-side panels only when both are useful together. At constrained widths, collapse secondary content before shrinking primary controls or labels.
- Add scrolling only to content that can exceed its region. Prefer vertical scrolling; wrap text before requiring horizontal scrolling.
- Tables: protect essential columns, let descriptive columns grow, and provide column hiding or a way to inspect truncated values.

### Dialogs and popups

- Use a dialog for a focused decision, form, or review. Use an anchored popup for a short action on its source item; use a tooltip only for supplemental help.
- Size dialogs by content: compact prompts/pickers target 420 px wide (350 px minimum); forms target 560 px; wide reviews use available width. Cap all widths at viewport minus 48 px.
- Limit dialog height to viewport minus 48 px. Keep title and actions visible; scroll the body when content exceeds the limit. Allow resizing for substantial content.
- Keep the dialog connected to the screen that opened it. Block the task only when a decision is required before continuing.
- Order content as title/context, task controls, then actions. Put cancel/secondary actions before the primary action; keep destructive actions separate.
- Start focus at the first useful control. Enter must not trigger an unsafe action; Escape cancels only when safe. Return focus to the opener on close.

### Buttons and actions

- Button height: at least 36 px; icon-only actions use a 36 × 36 px hit area. Let text buttons grow for labels and translation.
- Use one primary action per region. Put it at the trailing edge; order secondary/cancel before it. Separate destructive actions from the primary action.
- Use text labels where space allows. Icon-only actions need an accessible name and, when useful, a tooltip. Never rely on color or icon alone.
- Show unavailable actions as disabled rather than removing them. Preserve visible keyboard-focus and hover feedback.
- Localize labels and allow them to grow; do not clip or truncate action names.

## Viewport support

- Design and verify layouts at 1024 × 768 or wider. This is the supported desktop minimum.
- Below 1024 px, desktop layouts are unsupported. Do not compress controls to simulate support.
- At the minimum size, keep primary actions and content reachable; collapse secondary panels and allow optional table columns to be hidden.
