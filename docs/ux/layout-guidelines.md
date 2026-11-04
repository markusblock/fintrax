# UI layout guidelines

Concise guidance for new JavaFX views, panels, dialogs, popups, and buttons. Follow existing patterns; avoid inventing shared standards where none exist.

## Page structure

- Use `BorderPane` for page regions: header (`top`), main task (`center`), optional persistent status/actions (`bottom`). The app shell uses it for navigation and view content.
- View recipe: title and optional subtitle; related actions aligned at the trailing edge; primary work area; optional footer. Include empty, loading, and error states where relevant.
- Use `VBox` for vertical flow, `HBox` for related controls, `GridPane` for aligned forms, and `SplitPane` for simultaneously useful, user-resizable regions.
- Reuse `.view-header`, `.page-title`, `.view-subtitle`, and `.section-heading` where they fit. Keep one clear primary task; place secondary details in a supporting region.

## Spacing and surfaces

- Panel recipe: optional heading/context and actions; content; optional footer. Use `VBox`/`BorderPane`, existing `.content-panel`, `.settings-card`, or `.filter-panel` styles, and grow priorities for content that should expand.
- Keep fixed headers/footers outside a panel's scrolling content. Add a `ScrollPane` only around content that can exceed available space; fit its width when content can reflow.
- Prefer shared CSS in `ui/src/main/resources/css/fintrax.css` over inline styles or duplicated values. Use whitespace to group content; avoid decorative or nested panels without a clear hierarchy.
- Use theme variables (`-color-bg-*`, `-color-fg-*`, `-color-border-*`, semantic status colors) for both themes.

## Resizing and overflow

- Use `HBox.hgrow`, `VBox.vgrow`, fill constraints, and `maxWidth="Infinity"` for expanding content. Avoid fixed sizes on whole regions; constrain only where needed for usability.
- Give split panes usable proportions. Prefer vertical scrolling for long forms/settings; let text wrap and controls resize before requiring horizontal scrolling.
- Tables: keep essential columns usable, let descriptive columns grow, constrain stable-width columns, and provide a way to inspect truncated important values.
- Main window minimum is 1024 × 768. Smaller-window behavior is unsupported unless checked across the complete view.

## Forms, actions, and content

- Align forms with `GridPane`; let controls grow. Use localized resource-bundle text and allow translated labels to expand.
- Button styles: `.accent-button` for the primary action; `.button-outlined` for secondary actions; `.delete-button` for destructive actions; `.icon-button` for compact icon actions. Use text for actions where space allows.
- Reuse style classes instead of setting one-off dimensions. The filter panel's 36 × 36 icon actions are a compact reference, not a global size rule. Keep related buttons together and destructive actions distinct.
- In custom action rows, place the primary action trailing; in `Dialog`, use correct `ButtonData` and let JavaFX order the button bar. Preserve focus feedback and disable unavailable actions.
- Icon-only buttons need `accessibleText` and, where useful, a tooltip. Do not rely on icon or color alone. Wrap explanatory/user text; keep essential instructions and status readable.

## Dialogs and popups

- Use `Dialog`/`Alert` for a focused decision, input, or review; use `ContextMenu` for short actions on a specific item; use tooltips only for supplemental help. Do not put a multi-step form in a context menu.
- Give dialogs an owner and localized title. Choose blocking behavior deliberately (`showAndWait` when the user must decide before continuing); make substantial dialogs resizable.
- Organize dialog content as title/context, task controls, then actions. Use `ButtonType`/`ButtonBar` for dialog actions; keep cancel and destructive outcomes clear and distinct.
- Focus the first useful control when appropriate. Make Enter/Escape behavior safe, keep keyboard navigation usable, and prevent invalid submissions.
- Use `ScrollPane` for content that can exceed the dialog; size from content and avoid clipping translated text. Reuse `FintraxUI.configureDialog` and `.dialog-content` for styled dialogs.
- Keep context menus short and specific to their owner. Prefer visible controls for primary or persistent actions.

## Examples in the current UI

- View/split layout: [`classifications.fxml`](../../ui/src/main/resources/fxml/classifications.fxml)
- Panels and scrollable sections: [`settings.fxml`](../../ui/src/main/resources/fxml/settings.fxml), [`transactionFilterPanel.fxml`](../../ui/src/main/resources/fxml/transactionFilterPanel.fxml)
- FXML dialog and action row: [`addAccountDialog.fxml`](../../ui/src/main/resources/fxml/addAccountDialog.fxml)
- Programmatic dialog and button states: [`RuleReviewDialog.java`](../../ui/src/main/java/org/fintrax/ui/controller/RuleReviewDialog.java)
- Context menu: [`TransactionTableActions.java`](../../ui/src/main/java/org/fintrax/ui/controller/TransactionTableActions.java)
- Shared styles and theme colors: [`fintrax.css`](../../ui/src/main/resources/css/fintrax.css)

## Current gaps

- No shared spacing/button-size scale, full token reference, dialog-size standard, or global button-order rule exists. Follow the closest pattern; document a new shared rule only when it becomes intentional.
- No narrow-window breakpoint is defined. Do not claim support below the 1024 × 768 app minimum without checking it.

When departing from these guidelines, explain why in the change description. Update this guide when a new pattern should be shared.
