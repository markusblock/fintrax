# Transaction views share one workspace

Status: accepted

Transaction views need a consistent filter, transaction table, and read-only transaction details panel. The transactions, classifications, and rules views differ in their transaction source, columns, and row actions, but share the workspace interaction and layout.

Decision:

- Compose the transaction filter, table, and details panel in one reusable FXML workspace.
- Let each view provide its table columns and retain ownership of filtering, data refresh, and view-specific row actions.
- Show the filtered transaction count and amount total in the workspace headline, derived from the table's current items.
- Open transaction details on double-click in all views; keep the transactions view's explicit detail toggle.
- In views with category and label assignment cells, preserve those actions instead of opening details on double-click.

Consequences:

- Table sizing, filter presentation, and transaction details behavior stay consistent across the three views.
- The count and amount total update with each view's filtered transaction list.
- View-specific data sources and columns remain independently configurable without duplicating the workspace layout.
- New transaction views should reuse the workspace when their table and filter behavior fit this contract.
