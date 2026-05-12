-- Adds an audit reason for ad clicks that are stored without a user FK.
-- Apply before deploying application code that maps AdClickLog.anonymousReason.
-- Existing NULL values mean legacy or otherwise unclassified click rows.

ALTER TABLE ad_click_logs
    ADD COLUMN IF NOT EXISTS anonymous_reason VARCHAR(40);
