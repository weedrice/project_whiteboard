# Inquiry module V91 database change

V91 introduces the independent inquiry aggregate, immutable message and history tables, queue indexes,
configuration defaults, and the auto-close domain lock. Existing post, board, and comment rows are not
rewritten or migrated. Because the inquiry module has not been deployed before V91, its initial schema also
contains `inquiries.last_public_activity_at`, the active-inquiry guard index, and the auto-close queue index.

`inquiries.last_public_activity_at` is updated for every user message and public staff reply. Command reads use
an optimistic force-increment lock so a message append cannot commit behind a concurrent state transition without
participating in the aggregate's `version` contract. User-visible `modifiedAt` is the later of this public activity
time and the inquiry entity modification time; internal-note activity is therefore not exposed to the author.

The notification settings check constraint must be replaced so `INQUIRY` can be stored. The replacement
is backward compatible with all previously accepted values, but PostgreSQL requires a short exclusive
table lock while changing the constraint. The migration therefore uses a five-second lock timeout and
is classified as a contract migration by the repository safety gate. Deploy it during a normal low-write
window. `INQUIRY_NOTIFICATION_TYPE_ENABLED` starts at `N`: during the previous-JAR rollback window,
inquiry events are stored as the existing `SYSTEM` notification type and the dedicated preference is omitted
from notification-setting reads and writes. After the rollback window closes, change the gate once to `Y` to
persist `INQUIRY` events and expose their preference. The gate is intentionally one-way because changing it
back to `N` cannot make already persisted enum values readable by an older JAR.

V91 initially seeds `INQUIRY_LEGACY_WRITE_ENABLED=Y` so the backend foundation can be deployed before the
frontend cutover. The phase-three operational change sets it to `N`. Rollback does not delete new inquiry
data: setting it back to `Y` restores the legacy write path while the added tables remain intact.
