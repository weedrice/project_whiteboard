# Notification i18n Design

| Item | Value |
| --- | --- |
| Date | 2026-07-08 |
| Scope | Notification message rendering model |
| Status | Phase 1 implemented (2026-07-14) |

## Problem

Notification producers previously stored completed Korean message strings in
`notifications.content`. Phase 1 now stores structured message metadata while
retaining the completed content as a compatibility fallback.

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
  "messageParams": ["Alice"],
  "content": "Alice님이 회원님을 언급했습니다."
}
```

## Implemented Backend

- `V49__notification_message_i18n.sql` adds nullable `message_key` and
  `message_params` columns.
- `message_params` is stored as a JSON string and exposed as `List<String>`.
- Producers for comments, replies, likes, badges, scheduled posts, messages,
  mentions, and keyword matches publish structured keys and parameters.
- The command service renders and stores legacy `content` in the recipient's
  configured language, then dual-writes the structured metadata.
- `NotificationResponse` adds optional `messageKey` and `messageParams` fields;
  the existing `message` field and response envelope remain unchanged.
- Invalid or legacy parameter JSON degrades to an empty parameter list instead
  of failing notification reads.

## Implemented Frontend

- The API normalizer accepts camelCase and snake_case structured fields.
- Presentation resolves an allowlisted message key in the current app locale
  and falls back to legacy `message` for old or unsupported records.
- System and unknown actors use locale keys rather than English constants.
- API and SSE requests send the current locale through `Accept-Language`.
- A static i18n guard checks locale parity, registered static keys, and reviewed
  dynamic key families in CI.

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

## Remaining Decisions

- Whether to migrate `message_params` from text JSON to PostgreSQL `jsonb`.
- Whether admin/system notifications need richer params such as route labels or
  action buttons.
- Whether actor display names should be snapshotted at notification time or
  resolved live from actor IDs.
- Whether recent legacy rows need a backfill. They remain supported without one.
- Whether point-history descriptions should later adopt the same structured
  key/parameter model. They are currently localized when the ledger row is
  created, so changing the user's language does not re-render old history rows.

