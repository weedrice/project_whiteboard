# Mention Domain Design

| Item | Value |
| --- | --- |
| Date | 2026-07-08 |
| Scope | Mention persistence, parsing, and notification ownership |
| Status | Follow-up implementation plan |

## Problem

Mention logic is split across several places:

- Candidate search lives near user account APIs.
- Comment mention persistence lives in the comment domain.
- Comment mention notifications are triggered by `CommentCommandService`.
- Post mentions are parsed from saved HTML and do not yet have the same
  persistence model as comments.

This is acceptable at the current scale, but it will become harder to reason
about once posts and comments both need edit policies, history, and consistent
notification deduplication.

## Target Boundary

Create a `mention` domain package that owns mention parsing, normalization,
persistence orchestration, and notification policy.

Suggested backend package:

```text
domain/mention
  controller
  dto
  entity
  repository
  service
```

The first implementation can keep existing comment tables in place and introduce
the package as a service boundary before changing storage.

## Responsibilities

- Candidate search:
  - `GET /api/v1/users/mention-candidates` can remain as-is initially.
  - Internally delegate to `MentionCandidateService`.
- Mention extraction:
  - HTML extraction from `data-mention-user-id`.
  - Plain-text/comment metadata validation from explicit IDs.
- Mention persistence:
  - Replace comment mentions while preserving edit semantics.
  - Later add post mention records if post-level history is required.
- Notification policy:
  - Skip self mentions.
  - Respect block relationships.
  - Cap recipients per source to 10.
  - Deduplicate repeated mentions in one source.
  - Preserve the current edit behavior: update display metadata without sending mention notifications.

## Incremental Steps

1. Move candidate lookup logic behind a `MentionCandidateService`.
2. Move HTML and explicit-ID recipient resolution into `MentionRecipientResolver`.
3. Move notification publishing policy from `MentionService` into
   `MentionNotificationService`.
4. Keep `CommentMentionRepository` under comment until post mentions need a
   common storage model.
5. When post mention records are introduced, add a generic source model:
   `sourceType`, `sourceId`, `mentionedUserId`.

## Edit Policy

Current policy:

- Create: notify all valid mentioned users.
- Edit: update persisted display metadata without sending or resending notifications.
- Remove: remove display metadata when explicit mention metadata is removed.
- Metadata absent: preserve existing records.
- Empty metadata list: remove all records.

Changing edit behavior to notify newly added recipients is a separate product-policy change. It requires recipient-diff persistence and notification idempotency tests and must not be inferred from this domain refactor.

## Frontend Boundary

The frontend already has `features/mentions` for autocomplete UI. Future mention
rendering and parsing helpers should also live there, with domain-specific
wrappers in comments/posts only when needed.
