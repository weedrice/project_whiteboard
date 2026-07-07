# Notification i18n Design

| Item | Value |
| --- | --- |
| Date | 2026-07-08 |
| Scope | Notification message rendering model |
| Status | Follow-up implementation plan |

## Problem

Notification producers currently store completed Korean message strings in
`notifications.content`. This makes copy changes producer-dependent and prevents
the frontend from rendering the same notification in `ko` and `en`.

## Target Model

Keep the public notification envelope stable, then add a structured message model
behind it.

- Store `message_key` for the presentation string.
- Store `message_params` as JSON for actor names and object labels.
- Keep `content` during migration as a compatibility fallback.
- Let the frontend resolve display text from `messageKey + messageParams`.
- Fall back to `content` when the structured fields are absent.

Example:

```json
{
  "notificationType": "MENTION",
  "messageKey": "notification.message.mention",
  "messageParams": {
    "actorName": "Alice"
  },
  "content": "Alice님이 회원님을 언급했습니다."
}
```

## Backend Steps

1. Add nullable columns:
   - `notifications.message_key`
   - `notifications.message_params`
2. Add a small value object such as `NotificationMessage`.
3. Change producers to publish type-safe message keys and params.
4. Keep `NotificationResponse.content` populated with the existing fallback.
5. Add `messageKey` and `messageParams` to `NotificationResponse` only after the
   frontend is ready to consume them.

## Frontend Steps

1. Extend the notification normalizer to prefer `messageKey + messageParams`.
2. Add locale entries for each notification message key.
3. Keep fallback rendering from `content`.
4. Move producer-specific display logic into `features/notifications`.

## Migration Strategy

Phase 1:

- Add nullable DB columns and response fields.
- Producers write both structured fields and legacy `content`.
- Frontend reads structured fields when available.

Phase 2:

- Backfill recent notifications if needed.
- Stop relying on `content` for newly created notifications.

Phase 3:

- Decide whether `content` remains as denormalized fallback or is removed in a
  later breaking migration.

## Open Decisions

- Whether `message_params` should be `jsonb` in PostgreSQL and plain text in H2
  tests, or a string column with application-level JSON serialization.
- Whether admin/system notifications need richer params such as route labels or
  action buttons.
- Whether actor display names should be snapshotted at notification time or
  resolved live from actor IDs.

