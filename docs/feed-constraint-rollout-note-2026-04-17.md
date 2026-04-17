# Feed Constraint Rollout Note

`user_feeds` now has a unique constraint on:

- `target_user_id`
- `feed_type`
- `content_type`
- `content_id`
- `source_criteria`
- `criteria_id`

Before deploying this change to an existing PostgreSQL database, remove duplicate rows first.

## 1. Duplicate check

```sql
SELECT
    target_user_id,
    feed_type,
    content_type,
    content_id,
    source_criteria,
    criteria_id,
    COUNT(*) AS duplicate_count
FROM user_feeds
GROUP BY
    target_user_id,
    feed_type,
    content_type,
    content_id,
    source_criteria,
    criteria_id
HAVING COUNT(*) > 1;
```

## 2. Duplicate cleanup

Keep the newest row per logical feed key and delete the rest.

```sql
WITH ranked_feeds AS (
    SELECT
        feed_id,
        ROW_NUMBER() OVER (
            PARTITION BY
                target_user_id,
                feed_type,
                content_type,
                content_id,
                source_criteria,
                criteria_id
            ORDER BY created_at DESC, feed_id DESC
        ) AS row_num
    FROM user_feeds
)
DELETE FROM user_feeds
WHERE feed_id IN (
    SELECT feed_id
    FROM ranked_feeds
    WHERE row_num > 1
);
```

## 3. Post-cleanup verification

Re-run the duplicate check query and confirm it returns zero rows before starting the updated application.
