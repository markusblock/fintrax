# Hibiscus transaction imports deduplicate by source identity

Status: accepted

Importing the same Hibiscus export repeatedly must not create duplicate Fintrax transactions. A transaction checksum is the strongest source identity, but exports may omit it. Matching only on mutable descriptive data risks merging distinct transactions, while ignoring those fields when checksums are absent makes repeated imports non-idempotent.

Decision:

- Scope transaction identity to the associated bank account, preferring IBAN as the stable account identity.
- If both transactions have non-empty checksums, treat them as duplicates only when checksums match. Different checksums preserve distinct transactions even if their other fields match.
- If either checksum is absent, compare booking date, amount, normalized original payee, and normalized purpose in addition to account identity. Text normalization trims, collapses whitespace, and ignores case.
- Keep Hibiscus category matching unchanged. Hibiscus labels are not part of this import behavior.

Consequences:

- Re-importing a transaction with the same checksum skips creating it again.
- Missing checksums use the stable-field signature; distinct transactions identical on that signature cannot be distinguished and are treated as duplicates.
- A checksum is never compared across different bank accounts.
