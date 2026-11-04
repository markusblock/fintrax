# UI layout guidelines

Guidance for contributors changing Fintrax's JavaFX desktop UI. These rules capture patterns already used across the views and identify places where no shared standard exists yet. They are not a pixel-perfect design specification.

## Page structure

- Use `BorderPane` for a view's main regions: header in `top`, primary work area in `center`, and persistent supporting content in `bottom` only when needed. The application shell already uses a `BorderPane` for navigation and view content.
- Keep a clear hierarchy: page title, optional subtitle or short context, then the main task. Reuse `.view-header`, `.page-title`, `.view-subtitle`, and `.section-heading` from `fintrax.css` where they fit.
- Use `VBox` for vertically ordered content and `HBox` for related horizontal controls. Use `GridPane` for aligned label/control forms. Use `SplitPane` when both panes need to remain visible and users benefit from resizing them.
- Keep each view focused on its primary task. Put secondary settings or details in a supporting panel, section, or collapsible area rather than competing with the main content.

## Spacing and surfaces

- Prefer existing style classes and shared CSS in `ui/src/main/resources/css/fintrax.css` over per-node inline styles or duplicated style values.
- Use whitespace to group related controls and separate unrelated sections. Existing views use consistent gaps within groups and larger insets around page content; follow the nearest established pattern rather than introducing a new spacing scale.
- Reserve bordered, rounded surfaces such as `.content-panel`, `.settings-card`, and `.filter-panel` for meaningful groups. Avoid nesting decorative cards without a clear hierarchy.
- Use the theme color variables (`-color-bg-*`, `-color-fg-*`, `-color-border-*`, and semantic status colors) so surfaces remain legible in both themes.

## Resizing and overflow

- Let the layout grow with its window. Use JavaFX grow priorities (`HBox.hgrow`, `VBox.vgrow`) and fill constraints for the content that should take remaining space; use `maxWidth="Infinity"` for controls intended to fill their available column.
- Set minimum and preferred sizes only where they protect usability or communicate a meaningful default. Avoid fixed sizes on whole content regions; small fixed dimensions are appropriate for invariant controls such as table action columns.
- Give split panes usable initial proportions and enough room for each side. Check that important content remains accessible when panes are resized.
- Put potentially long content in a `ScrollPane` or another suitable scrolling control. Prefer vertical scrolling for forms and settings; avoid forcing horizontal scrolling when content can wrap or resize.
- For tables, set minimum widths that keep values and actions usable, allow descriptive columns to take remaining space, and constrain only columns whose content has a stable size. Long table cells may truncate; provide a way to inspect important full values.
- The main window currently has a minimum size of 1024 × 768. Do not claim support below that size without checking the complete view at the smaller dimensions.

## Forms, actions, and content

- Align form labels and controls with `GridPane` constraints. Let input controls grow where possible and keep related fields visually grouped.
- Use concise, localized visible labels. Define user-facing text through the resource bundles rather than hard-coding it in FXML. Allow for translations that are longer than the current English text; avoid widths that clip labels or buttons.
- Make the primary action visually distinct with the established accent style. Keep destructive actions visually identifiable and separate from routine actions.
- Icon-only controls need an accessible name and, where useful, a tooltip. Do not communicate meaning through color or an icon alone.
- Wrap explanatory or user-provided text when it needs to be read in full. Use muted text styles for secondary explanations, not for essential instructions or status.
- Consider empty, error, and long-content states when sizing a view; the layout should still explain what the user can do next.

## Examples in the current UI

- Application shell and navigation: [`main.fxml`](../../ui/src/main/resources/fxml/main.fxml)
- Page header and resizable split layout: [`classifications.fxml`](../../ui/src/main/resources/fxml/classifications.fxml)
- Search/filter controls and accessible icon actions: [`transactionFilterPanel.fxml`](../../ui/src/main/resources/fxml/transactionFilterPanel.fxml)
- Scrollable settings sections and aligned form fields: [`settings.fxml`](../../ui/src/main/resources/fxml/settings.fxml)
- Shared view styles and theme colors: [`fintrax.css`](../../ui/src/main/resources/css/fintrax.css)

## Current gaps

- There is no documented spacing scale or full design-token reference. Follow existing shared styles and local patterns; do not infer a new numeric system from one view.
- The application enforces a 1024 × 768 minimum window size and has no documented narrow-window breakpoint. Smaller-window or mobile behavior needs an explicit design decision before being treated as supported.

When a view must depart from these guidelines, keep the reason clear in the UI or its change description and update this guide if the new pattern should be shared.
