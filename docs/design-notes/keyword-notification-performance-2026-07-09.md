# Keyword Notification Performance

Current implementation checked: 2026-08-13

## Current State

Keyword notification matching intentionally keeps a simple reverse substring query expressed with JPQL `LOCATE`:

```jpql
LOCATE(LOWER(subscription.keyword), LOWER(:title)) > 0
```

Both `findMatchingTitle` and the cursor-based `findMatchingTitleAfter` use this predicate. It cannot use a normal
keyword index because the post title is the searched value and every stored keyword must be located inside it.

The execution path is durable rather than an after-commit in-memory task:

1. `KeywordNotificationEventListener` inserts one `keyword_notification_fanout_jobs` row per published post in
   the publishing transaction (`BEFORE_COMMIT`). The unique post constraint makes repeated enqueue attempts safe.
2. `NotificationDeliveryJobScheduler` polls every 10 seconds and delegates due jobs to
   `KeywordNotificationFanoutProcessor`.
3. The processor locks a job, scans matching subscriptions in `subscriptionId` order with a batch size of 100,
   stores the last processed subscription ID as its cursor, and enqueues durable notification delivery jobs.
4. A failed page retries after one minute. Five failures move the fan-out job to `FAILED`; pending count,
   dead-letter count, oldest-due age, and retry count are exported as metrics.

Post publishing therefore performs only the durable job insert. Keyword matching, notification creation, and SSE
delivery run outside the post creation response path and survive process restarts.

## Redesign Threshold

Keep the current query while keyword subscriptions are at low thousands scale. Revisit the design when either
condition is met:

- total keyword subscriptions regularly exceed several thousand rows, or
- fan-out pending/dead-letter count, oldest-due age, or matching-query latency shows a sustained backlog.

The likely next design is tokenized matching or an inverted keyword index built from normalized keyword tokens.
