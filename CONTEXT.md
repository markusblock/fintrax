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

**Sibling Categories**:
Categories sharing the same parent. Top-level categories are siblings when they have no parent.

**Category Tree**:
The full hierarchy of top-level categories and their descendants.

**Category Identity**:
A category is identified by its name together with its parent. Sibling names must be unique; the same name under a different parent is allowed.

### Category Import

**Hibiscus Category**:
A category defined in a Hibiscus XML export, carrying a hibiscus ID, a name, an optional parent hibiscus ID, an optional color, and an optional matching pattern.
_Avoid_: imported category

**Fintrax Category**:
A category stored in Fintrax.
_Avoid_: target category

**Full Category Export**:
A Hibiscus export that contains every category referenced as another category's parent. Category imports require a full export.

**Category Import**:
The operation that reads Hibiscus Categories and creates or matches Fintrax Categories while preserving their hierarchy.

**Category Match**:
The association between a Hibiscus Category and an existing Fintrax Category during one import, based on category name and parent.

**Unresolved Parent**:
A parent reference that cannot be mapped to a Fintrax Category during import. Under the full-export rule this is invalid input and fails the import.

**Category Rule**:
A rule derived from a Hibiscus Category's matching pattern that assigns matching transactions to the mapped Fintrax Category.

**Pattern Match Mode**:
The way a Category Rule evaluates its pattern: regex or contains matching.

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
