# User Story: Reset Fintrax Data

As a Fintrax user, I want to choose which groups of local data to reset from Settings, so I can remove unwanted data without deleting unrelated data.

## Acceptance Criteria

- Settings contains a Reset data section below Import.
- Reset opens a selection dialog with all groups unchecked:
  - Accounts, transactions, sync history, and activity log
  - Categories, rules, and labels
  - Stored bank credentials
  - Application settings
- Reset remains disabled until at least one group is selected.
- Select all and Clear all controls are available.
- The dialog warns that reset is irreversible and selected data is deleted immediately.
- No typed confirmation or confirmation dialog is required in development mode.
- Resetting categories, rules, and labels clears `categoryId` and `labelIds` from retained transactions.
- Resetting stored bank credentials removes all stored PINs independently of account reset.
- Resetting application settings clears persisted settings only.
- The operation runs under the store write lock, persists changed collections, and rebuilds indexes.
- The running application refreshes in place after success.
- Reset is not offered while a sync or import operation is active.
