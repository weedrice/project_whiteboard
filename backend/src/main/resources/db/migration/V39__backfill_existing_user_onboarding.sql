UPDATE user_settings
SET onboarding_completed_at = COALESCE(modified_at, created_at, CURRENT_TIMESTAMP)
WHERE onboarding_completed_at IS NULL;
