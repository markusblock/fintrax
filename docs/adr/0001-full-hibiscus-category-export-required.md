# Full Hibiscus category exports are required

Status: accepted

Category imports only accept full Hibiscus category exports — files that contain every category referenced as another category's parent. We chose this over permissive best-effort import because a partial or internally inconsistent export has no reliable way to place children; silently repairing the hierarchy (e.g. promoting an orphan to top-level) produces a tree the user never asked for and makes results depend on XML element order.

Consequences:

- Missing or circular parent references fail the import with a clear error instead of being repaired.
- Parent categories are resolved by their hibiscus ID within the same export, never by name lookup, so XML object order does not affect the resulting category tree.
- Source data that duplicates a sibling name (same name, same parent) is rejected rather than merged, since it cannot be represented unambiguously.
