# Local data backups and recovery

Fintrax uses Eclipse Store's supported backup APIs; it does not copy live storage files to create backups.

## Automatic backups

- Eclipse Store continuously mirrors committed changes to `<backup folder>/continuous` while Fintrax is running. The mirror may briefly lag the active store; a clean shutdown flushes pending backup work.
- On every app launch, Fintrax creates a full point-in-time backup under `<backup folder>/snapshots` and retains the 30 newest completed copies. An incomplete backup is not published as a completed restore point. Full copies run off the UI thread but temporarily pause store operations; large stores may delay data work during the copy.
- The default backup folder is `~/.fintrax/backups`. Change it in **Settings → Backups**. Fintrax applies a changed folder after restart because Eclipse Store's continuous-backup target is configured before the store starts.
- If backup setup or a full backup fails, Fintrax asks the user to choose another folder and restart, or exit. Continuous-backup disruptions can disable storage operations until recovery; full-backup failures preserve previous completed copies.
- If Eclipse Store cannot start because stored data is unreadable, Fintrax shows the storage error and offers completed full backups. Restore copies the selected backup to a sibling staging directory, validates that it opens, preserves the failed storage (and old continuous mirror) under timestamped names, then makes the validated copy active. Storage-already-in-use is reported separately; it is not treated as corruption.
- The old mirror is archived beside `continuous` as `continuous.pre-restore-<id>`. It is not used by the active store and may contain commits newer than the chosen full backup. Keep it until the restored data and new `continuous` mirror are verified; then delete it if no extra recovery data is needed. Do not delete the current `continuous` directory.
- The default folder is on the same disk as Fintrax data. Configure a folder on another disk or storage location to protect against disk failure.
- Eclipse Store does not provide lightweight historical snapshots every 15 minutes. Continuous backup is one evolving mirror; `issueFullBackup` makes a complete blocking copy. Fintrax does not compress backup folders on close; clean shutdown flushes the continuous mirror.

## Test a full-backup restore before replacing active data

1. Close Fintrax. Choose a completed directory under `snapshots` and copy that offline backup to a separate test directory. Do not copy the active storage while Fintrax is running. The startup recovery dialog performs this separate-path validation automatically before promoting a restore.
2. Start Fintrax with the test directory as its storage path and with a separate backup destination/config file. For example:

   ```sh
   ./gradlew :app:run --args="--fintrax.storage.path=$HOME/.fintrax/restore-test --fintrax.backup.path=$HOME/.fintrax/restore-test-backups --fintrax.backup.config=$HOME/.fintrax/restore-test-backup.properties"
   ```

   Copy the selected full-backup directory's contents into `~/.fintrax/restore-test` before running this command. Keep the active data path unchanged.

3. Confirm Fintrax starts and inspect representative accounts, transactions, categories, rules, and settings. Close the test instance.
4. Only after validation, make a separate copy of the active data while Fintrax is stopped. Then either point `fintrax.storage.path` at the validated restore or replace the active directory with the validated copy. Keep the pre-restore copy until the restored application has been checked.

Continuous backup is a current mirror, not a retained set of historical snapshots. For manual recovery, close Fintrax before copying that directory; prefer a completed full backup when a historical restore point is needed.
