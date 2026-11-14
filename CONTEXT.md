# Fintrax

Personal finance application that imports banking data and supports automatic transaction categorization.

## Language

### Categories

**Category**:
A classification node used to organize transactions. A category may be top-level or nested under exactly one parent category.
_Avoid_: category type, category record

**Top-Level Category**:
A category without a parent.

**Parent Category**:
The category a child category is nested directly under.

**Child Category**:
A category nested directly under a parent category.

**Category Move**:
Changing a category's parent while keeping its descendants attached. Choosing no parent makes it a top-level category.

**Sibling Categories**:
Categories sharing the same parent. Top-level categories are siblings when they have no parent.

**Category Tree**:
The full hierarchy of top-level categories and their descendants.

**Category Identity**:
A category is identified by its name together with its parent. Sibling names must be unique without regard to letter case; the same name under a different parent is allowed.

**Label Identity**:
A label is identified by its name, compared without regard to letter case; labels with the same name are duplicates.

### Category Import

**Hibiscus Category**:
A category defined in a Hibiscus XML export, carrying a hibiscus ID, a name, an optional parent hibiscus ID, and an optional matching pattern.
_Avoid_: imported category

**Fintrax Category**:
A category stored in Fintrax.
_Avoid_: target category

**Full Category Export**:
A Hibiscus export that contains every category referenced as another category's parent. Category imports require a full export.

**Category Import**:
The operation that reads Hibiscus Categories and creates or matches Fintrax Categories while preserving their hierarchy.

**Category Match**:
The association between a Hibiscus Category and an existing Fintrax Category during one import, based on category name (ignoring letter case) and parent.

**Unresolved Parent**:
A parent reference that cannot be mapped to a Fintrax Category during import. Under the full-export rule this is invalid input and fails the import.

**Category Rule**:
A rule, possibly derived from a Hibiscus Category matching pattern, that matches transactions. For overlapping matches, the highest-priority rule supplies the category while every matching rule contributes labels without duplicates. Category Rules do not modify transaction notes. Applying rules to existing transactions requires reviewing proposed changes before saving them.

### Rules

**Rule Identity**:
A rule is identified for portable export and import by its name, compared without regard to letter case; rule names must be unique.

**Rule Export**:
A portable archive containing a selected set of data and only the references needed to use it; the current export includes all rules. A Rule Export is not a backup of other application data.

**Rule Import**:
A merge that adds new rules and updates same-name rules while leaving local rules absent from the export unchanged. Missing or ambiguous category, label, or account references make the whole import invalid.

**Rule Condition**:
A comparison between a transaction field and a search value. Text comparisons, including regular expressions, ignore letter case.

**Rule Match Mode**:
How a rule combines its conditions: **all** requires every condition to match; **any** requires at least one. Existing rules without an explicit match mode use **all**.

**Rule Action**:
An assignment made when a rule matches: assign one category or add a label.

**Imported Category Rule Match**:
An existing rule with the same payee-or-purpose condition, operator, pattern, and mapped category action. Rule name, priority, and enabled state do not affect the match.

**Pattern Match Mode**:
The way a Category Rule evaluates its pattern: regex or contains matching.

### Accounts

**Bank**:
A bank connection identity shared by one or more Bank Accounts, matched by BLZ when available. Stores bank name, BIC, BLZ, FinTS server address, FinTS User ID, optional Customer ID, and comment; PIN/password is never persisted.

**FinTS User ID**:
The login identifier used for a Bank's FinTS connection; it is distinct from an account's display name or account holder.

**FinTS Customer ID**:
A bank-specific identifier supplied separately from the FinTS User ID when required; it may be blank when the bank instructs.

**Bank Account**:
An individual financial account identified by IBAN and linked to one Bank; its account name, account holder, type, currency, and comment are account-specific.

**Fintrax Account Identity**:
The stable identity of a Fintrax account: its internal account ID, associated with an IBAN when available. The IBAN identifies the bank account for sync and Hibiscus matching; it is not changed when editing account details.

**Bankleitzahl (BLZ)**:
The German bank routing code encoded in a German IBAN and used to identify the bank for FinTS.

**BIC**:
A code identifying a financial institution; it is not a FinTS server address.

**FinTS Server Address**:
The HTTPS endpoint used to communicate with a bank through FinTS.

### Account Import

**Hibiscus Account**:
An account defined in a Hibiscus XML export, carrying an optional IBAN and account details. Map `<bezeichnung>` to Bank Account name and `<name>` to Bank Account holder; neither supplies FinTS User ID.

**Account Match**:
An existing Fintrax account with the same normalized, non-empty IBAN. A match reuses the Fintrax account without changing its details; an account without an IBAN is not matched.

### Transaction Import

**Hibiscus Transaction**:
A transaction defined in a Hibiscus XML export, carrying an account reference, booking date, amount, payee, purpose, and optional checksum.

**Transaction Match**:
An association between an incoming Hibiscus Transaction and an existing Fintrax transaction during import. When both have checksums, they match only when checksums and account identities match. If either checksum is absent, they match by account identity, booking date, amount, and normalized payee and purpose.

### Transactions

**Transaction Details**:
User-facing information describing a transaction, including its account, dates, payees, purpose, amount, balance, type, booking status, and bank references. Source checksums, internal identifiers, and audit timestamps are not transaction details.

**Booked Transaction**:
A transaction the financial institution confirms as booked.

**Pending Transaction**:
A transaction the financial institution reports as unbooked; it may later become a Booked Transaction.

**Bank Transaction Sync**:
An on-demand retrieval of transactions for a Fintrax Account from its financial institution. Bank Sync Exclusion omits an account from this operation while keeping its existing local data.

**Bank Sync Exclusion**:
A per-Bank Account setting that prevents it from being included in Bank Transaction Sync, including Sync All. Existing local transactions and account data remain available.

### Data Reset

**Data Reset**:
An operation that deletes one or more selected groups of Fintrax-owned data from the local application.
_Avoid_: database wipe, cleanup

**Reset Group**:
A user-facing set of related data selected together during a Data Reset.

**Stored Credential**:
A bank PIN held by Fintrax for an account connection, separately from the account and transaction data.

**Stored Setting**:
An application setting persisted in Fintrax and eligible for removal by the Application Settings Reset Group.
