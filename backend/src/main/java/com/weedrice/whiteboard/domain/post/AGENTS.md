# Post Domain AGENTS.md

## Scope

This domain owns post reads and commands, drafts, and scheduled publication. Follow this guidance for their tests and integration callers as routed by the backend instructions.

## Sources

- For draft promotion, file reservations, or their cleanup/concurrency changes, read the [post/file lifecycle](../../../../../../../../../docs/design-notes/post-file-lifecycle.md).
- Use [PostAccessPolicy](service/PostAccessPolicy.java) and [PostAuthorCommandPolicy](service/PostAuthorCommandPolicy.java) for read and author-command policy.
- Use [PostDraftPublicationService](service/PostDraftPublicationService.java), [PostCreateSideEffectService](service/PostCreateSideEffectService.java), and [ScheduledPostPublishWorker](scheduled/service/ScheduledPostPublishWorker.java) for publication ordering and transaction boundaries.
- Consult the applied [content access contract note](../../../../../../../../../docs/design-notes/content-access-contract-hardening-2026-07-18.md) when changing visibility, navigation, partial updates, or pagination.

## Invariants

- Reuse the access and author-command policies. Ordinary reads of deleted or blinded posts remain unavailable before author/manager exceptions; moderation access uses its own flow.
- Validate an owned source draft under its existing lock, including board and original-post identity. A draft for editing an existing post cannot become a scheduled new post.
- Preserve protection of drafts referenced by `SCHEDULED`, `PUBLISHING`, or `FAILED` schedules across save, recovery, deletion, eviction, and expiry. Only the matching internal publication flow may consume its protected draft.
- Keep `publishingScheduledPostId` internal to scheduled publication. Do not expose a client-controlled reservation bypass in request DTOs.
- Keep file promotion, remaining draft-file cleanup, and draft deletion in the shared create/update flow. A new entry point must preserve its rewards, mentions, publication events, indexing, and badge side effects where applicable.
- Scheduled completion and failure must match both status and `processingStartedAt`; a stale worker must not finalize a newer lease. Preserve the existing separate claim, publication, and failure transactions.

## Impact

- For file or draft changes, trace ordinary creation/editing, autosave/recovery, scheduled creation/update/cancel/retry, and both draft and scheduled cleanup callers.
- For visibility changes, inspect board policy, comment/file readers, search/series/navigation, and Agent integration consumers; a detail-page check alone is insufficient.
- For list or partial-update changes, preserve stable ordering and the existing omitted/null/value distinctions. Check editor, draft, and scheduled-post frontend consumers and the backend API specifications.

## Verification

Use the backend Verification command with affected classes from `src/test/java/com/weedrice/whiteboard/`:

- Reads and commands: `PostAccessPolicyTest`, `PostReadContextResolverTest`, and `PostServiceTest`.
- Draft lifecycle: `PostDraftFileReferenceTest`, `PostDraftCleanupServiceTest`, `PostDraftCleanupCoordinatorTest`, and `PostDraftCleanupBatchServiceTest`.
- Scheduled lifecycle: `ScheduledPostServiceTest`, `ScheduledPostFileServiceTest`, `ScheduledPostPublishWorkerTest`, and the affected `ScheduledPostCleanup*Test` classes.
- Shared reservation behavior: `FileAssociationReservationTest`.
