# Hibiscus transaction imports deduplicate by source identity

Status: accepted

Importing the same Hibiscus export repeatedly must not create duplicate Fintrax accounts, transactions, categories, or category rules. A transaction checksum is the strongest source identity, but exports may omit it. Matching only on mutable descriptive data risks merging distinct transactions, while ignoring those fields when checksums are absent makes repeated imports non-idempotent.

Decision:

- Scope transaction identity to the associated bank account, using normalized IBAN when present; otherwise, use the Fintrax account ID so distinct IBAN-less accounts are never conflated.
- If both transactions have non-empty checksums, treat them as duplicates only when checksums match. Different checksums preserve distinct transactions even if their other fields match.
- If either checksum is absent, compare booking date, amount, normalized original payee, and normalized purpose in addition to account identity. Text normalization trims, collapses whitespace, and ignores case.
- Match accounts by normalized non-empty IBAN. Reuse matching Fintrax accounts without updating their details. Do not guess an account match when IBAN is absent.
- Match imported category rules by their behavior: mapped category action plus payee condition, operator, and pattern. Ignore rule name, priority, and enabled state; never re-enable a matching disabled rule.
- Keep Hibiscus category matching unchanged. Hibiscus labels are not part of this import behavior.

Consequences:

- Re-importing a transaction with the same checksum skips creating it again.
- Re-importing an account with the same IBAN or a category rule with the same behavior reuses the existing Fintrax data.
- Missing checksums use the stable-field signature; distinct transactions identical on that signature cannot be distinguished and are treated as duplicates.
- A checksum is never compared across different bank accounts.
- Accounts without IBANs cannot be deduplicated safely and are imported as new records.
- Transactions attached to IBAN-less accounts cannot be matched across imports because each import creates a new Fintrax account identity.
