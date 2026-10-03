# File Domain AGENTS.md

## Scope

This domain owns upload validation, file metadata and variants, association, access, and storage cleanup. Follow this guidance for its tests and callers in other domains.

## Sources

- For association, reservation, or related cleanup changes, read the [post/file lifecycle](../../../../../../../../../docs/design-notes/post-file-lifecycle.md).
- Use [FileService](service/FileService.java), [FileAssociationService](service/FileAssociationService.java), and [FileRepository](repository/FileRepository.java) for association entry points and database conditions.
- Use [FileAccessService](service/FileAccessService.java), [FileUploadValidationPolicy](service/FileUploadValidationPolicy.java), and [FileUploadTarget](service/FileUploadTarget.java) for access and upload policy.
- Use [FileDeletionWorker](service/FileDeletionWorker.java) for deletion claims, storage effects, finalization, and retries.

## Invariants

- Route association through the shared file service. The uploader, active storage state, current association, and scheduled reservation all constrain reuse; the same owner alone does not authorize reassociation.
- Preserve sorted file locking, refresh/revalidation, the separate post-lock reservation query, and conditional UPDATE guards described in the lifecycle note. Do not replace them with an unlocked Java precheck.
- Keep reservation checks before same-target early returns. Only the matching internal scheduled publication context may consume reserved files; generic associations and ordinary posts cannot.
- Draft/post synchronization locks requested and existing target files together before mutation. Validate the requested set before marking omitted files for deletion, and preserve request display order independently of lock order.
- Keep server-selected association target validation as well as upload-time validation. A client-supplied upload target is not sufficient evidence for profile, board-icon, or other target limits.
- File reads must honor the related resource's access policy and returned cache scope. Preserve the special emoticon-reference handling; do not generalize every unassociated file into either a public or uploader-only resource.
- Keep metadata transitions separate from physical deletion. The database claim coordinates workers; the in-process set only reduces duplicate work. Preserve variant/original deletion order, snapshot checks, reference safeguards, and retry handling.

## Impact

- Check posts/drafts/schedules, profiles, board icons, emoticons, and inquiry attachments when a shared association method changes.
- Check temporary discard/TTL cleanup and deletion workers when changing reservation or storage eligibility. Reservation removal does not itself delete the stored object.
- Check download/image consumers and cache headers when access policy changes; retain existing error precedence and response envelopes.

## Verification

Use the backend Verification command with affected classes under `src/test/java/com/weedrice/whiteboard/domain/file/` and the calling domain:

- Association: `FileAssociationReservationTest`, `FileServiceTest`, `FileRepositoryTest`, and `PostDraftFileReferenceTest`.
- Upload/access: `FileUploadValidationPolicyTest`, `FileUploadStateCommandTest`, `FileAccessServiceTest`, and `FileDownloadServiceTest`.
- Cleanup: `FileUploadDiscardRepositoryTest`, `FileTemporaryCleanupTtlContractTest`, `FileTemporaryCleanupWorkerTest`, and `FileDeletionWorkerTest`.
- Include scheduled service/worker tests for reservation changes.
