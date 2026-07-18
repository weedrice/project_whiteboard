# Content Access and Post Contract Hardening (2026-07-18)

## Scope

This change hardens post, series, board ranking, poll, and moderation-audit behavior without changing endpoint URLs or response envelopes.

## Read access

- Blinded posts are not readable through ordinary post, comment, poll, or related-content paths. They are returned as `POST_NOT_FOUND`, including for the author and board managers.
- Moderation commands keep using their dedicated moderation lookup and authorization paths, so managers can still unblind or otherwise moderate a blinded target.
- Series navigation is calculated only from posts readable by the current viewer. Deleted, blinded, secret, private/inactive-board, and blocked-author posts do not contribute links, index, or total count.

## Post write contract

- Nested poll validation applies to post creation and update request binding.
- Poll creation is also validated at the service boundary: question length is at most 200, option length is at most 100, option count is 2 to 10, and `closesAt` must be in the future.
- Polls are immutable through `PUT /api/v1/posts/{postId}`. A non-null `poll` payload is rejected with `INVALID_INPUT_VALUE`; voting remains available through the dedicated poll endpoints.
- `seriesId` has three update states:
  - omitted: preserve the current series assignment;
  - positive ID: attach or move the post to that owned series;
  - explicit `null`: remove the post from its series.
- Series attachment locks the target series row before calculating `MAX(sortOrder) + 1`, serializing concurrent appends without a schema migration.

## Ranking and audit pagination

- Blinded posts are excluded from board post-count rankings.
- Moderation audit sorting accepts only `auditId`, `createdAt`, `actorType`, `adminId`, `action`, `targetType`, and `targetId`.
- Unsupported audit sort properties are discarded. The default order is `createdAt,DESC` then `auditId,DESC`, and the unique `auditId` tie-breaker is appended to supported custom sorts.
