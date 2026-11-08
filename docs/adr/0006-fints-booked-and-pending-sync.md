# FinTS sync reconciles pending entries only from complete responses

Use FinTS `KUmsAll` without a client-selected start date so the bank-supported history window bounds each repeated sync; transaction matching follows ADR-0004. Import booked and pending transactions, update matching pending entries when they become booked while preserving Fintrax-owned fields, and remove unmatched pending entries only after HBCI execution, the transaction job, and mapping every returned row succeed. Request PIN for each validation or sync without storing it; abort unsupported TAN challenges without sending a response, retain TLS certificate validation, and avoid logging financial data. Persistent credential storage remains a separate decision tracked by #146.

This may retrieve more history than a narrow incremental window, but avoids fixed-window gaps and keeps stale pending cleanup tied to complete bank data.
