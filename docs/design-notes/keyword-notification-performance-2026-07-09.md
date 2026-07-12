# Keyword Notification Performance

## Current State

Keyword notification matching intentionally keeps the simple reverse LIKE query:

```sql
LOWER(:title) LIKE '%' || LOWER(keyword) || '%'
```

This cannot use a normal keyword index because the post title is the searched value and each stored keyword is
the pattern. The listener now runs asynchronously after commit, so matching, notification inserts, and SSE
delivery no longer add latency to the post creation response.

## Redesign Threshold

Keep the current query while keyword subscriptions are at low thousands scale. Revisit the design when either
condition is met:

- total keyword subscriptions regularly exceed several thousand rows, or
- post publishing latency / async queue depth shows keyword matching as a repeated bottleneck.

The likely next design is tokenized matching or an inverted keyword index built from normalized keyword tokens.
