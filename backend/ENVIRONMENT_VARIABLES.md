# Backend Environment Variables

This document is the production environment variable checklist for the NoviIs backend.
Do not commit real secret values. Configure values through the deployment environment,
shell environment, CI/CD secrets, or an approved secret manager.

## Required In Production

| Variable | Purpose |
| --- | --- |
| `DB_HOST` | PostgreSQL host |
| `DB_NAME` | PostgreSQL database name |
| `DB_USER` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | JWT signing secret |
| `GITHUB_CLIENT_ID` | GitHub OAuth client id |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth client secret |
| `GOOGLE_CLIENT_ID` | Google OAuth client id |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `DISCORD_CLIENT_ID` | Discord OAuth client id |
| `DISCORD_CLIENT_SECRET` | Discord OAuth client secret |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_APP_PASSWORD` | SMTP app password |
| `AWS_ACCESS_KEY` | AWS access key for storage integration |
| `AWS_SECRET_KEY` | AWS secret key for storage integration |
| `AWS_S3_REGION` | AWS S3 region |
| `S3_BUCKET` | S3 bucket name |
| `FRONTEND_URL` | Public frontend origin used in links and redirects |
| `AGENT_INTERNAL_SECRET` | Shared secret for trusted internal agent requests |

## Optional Or Deployment-Specific

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile. Production should include `prod`. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | Reverse proxy header handling override when needed. |
| `LOG_PATH` | Production log directory. Defaults to `/opt/app/logs`. |
| `FLYWAY_BASELINE_ON_MIGRATE` | Enables Flyway baseline-on-migrate. Defaults to `false`. |
| `APP_THUMBNAIL_ALLOWED_EXTERNAL_HOSTS` | Comma-separated exact HTTPS hosts allowed for external post thumbnails. Defaults to `noviis.kr,www.noviis.kr,cdn.noviis.kr`; keep every public frontend alias when `FRONTEND_URL` changes. |
| `APP_CACHE_READ_OPTIMIZATION_ENABLED` | Enables the application read-optimization cache path. Defaults to `false`. |
| `APP_ERROR_LOG_CLIENT_RETENTION_DAYS` | Client error-log retention period. Defaults to `30` days. |
| `APP_ERROR_LOG_RESOLVED_RETENTION_DAYS` | Resolved error-log retention period. Defaults to `90` days. |
| `APP_ERROR_LOG_CLEANUP_BATCH_SIZE` | Maximum error-log cleanup batch size. Defaults to `500`. |
| `APP_MESSAGE_QUEUE_TERMINAL_RETENTION_DAYS` | Terminal message-queue retention period. Defaults to `30` days. |
| `APP_MESSAGE_QUEUE_DELIVERED_UNCONFIRMED_RETENTION_DAYS` | Delivered-but-unconfirmed message retention period. Defaults to `7` days. |
| `APP_MESSAGE_QUEUE_CLEANUP_BATCH_SIZE` | Maximum message-queue cleanup batch size. Defaults to `500`. |
| `APP_VERIFICATION_CODE_TERMINAL_RETENTION_DAYS` | Terminal verification-code retention period. Defaults to `7` days. |
| `APP_VERIFICATION_CODE_PENDING_RECOVERY_GRACE_MINUTES` | Grace period before pending verification-code recovery. Defaults to `30` minutes. |
| `APP_VERIFICATION_CODE_PENDING_RECOVERY_BATCH_SIZE` | Maximum pending verification-code recovery batch size. Defaults to `500`. |
| `APP_VERIFICATION_CODE_PENDING_RECOVERY_MAX_BATCHES` | Maximum pending verification-code recovery batches per run. Defaults to `10`. |
| `APP_PASSWORD_RESET_TOKEN_RETENTION_DAYS` | Expired password-reset-token retention period. Defaults to `30` days. |
| `APP_PASSWORD_RESET_TOKEN_CLEANUP_BATCH_SIZE` | Maximum password-reset-token cleanup batch size. Defaults to `500`. |
| `APP_VERIFICATION_CODE_CLEANUP_BATCH_SIZE` | Maximum verification-code cleanup batch size. Defaults to `500`. |
| `APP_AGENT_PENDING_CLAIM_HARD_DELETE_DAYS` | Age threshold for permanently deleting pending Agent claims. Defaults to `7` days. |
| `APP_AGENT_PENDING_CLAIM_PURGE_BATCH_SIZE` | Maximum pending Agent claim purge batch size. Defaults to `500`. |
| `APP_AGENT_PENDING_CLAIM_PURGE_MAX_BATCHES` | Maximum pending Agent claim purge batches per run. Defaults to `10`. |
| `SEMANTIC_SEARCH_ENABLED` | Enables OpenAI/pgvector semantic search. Defaults to `false`. |
| `OPENAI_API_KEY` | OpenAI API key used for embeddings when semantic search is enabled. |
| `SEMANTIC_SEARCH_RELATED_POST_MIN_SIMILARITY` | Minimum similarity for related posts. Defaults to `0.55`. |
| `WEB_PUSH_PUBLIC_KEY` | Browser-facing VAPID public key. |
| `WEB_PUSH_PRIVATE_KEY` | Secret VAPID private key used to sign Web Push requests. |
| `WEB_PUSH_SUBJECT` | VAPID contact subject, normally an `https:` or `mailto:` URI. |
| `DB_MAX_POOL_SIZE` | Production Hikari maximum pool size override. Defaults to `20`. |
| `DB_MIN_IDLE` | Production Hikari minimum idle connection override. Defaults to `5`. |
| `RATE_LIMIT_AUTH_ACCOUNT_LIMIT` | Per-account auth rate-limit capacity override. |
| `RATE_LIMIT_BUCKET_CACHE_MAX_SIZE` | Maximum rate-limit bucket cache size override. |
| `RATE_LIMIT_BUCKET_CACHE_TTL_MINUTES` | Rate-limit bucket cache time-to-live override. |
| `CLIENT_IP_TRUST_PROXY_HEADERS` | Enables trusted proxy header parsing. Defaults to `true` in prod. |
| `CLIENT_IP_TRUSTED_PROXIES` | Comma-separated trusted proxy IP/CIDR list. |

### Conditional Feature Activation

- Semantic vector search requires `SEMANTIC_SEARCH_ENABLED=true` and a non-blank
  `OPENAI_API_KEY`. Without both values, search uses the keyword fallback path.
- Web Push requires non-blank `WEB_PUSH_PUBLIC_KEY`, `WEB_PUSH_PRIVATE_KEY`, and
  `WEB_PUSH_SUBJECT` values. If any value is missing, Web Push remains disabled while
  stored notifications and SSE delivery continue to work.
- These feature-specific values are optional for the backend as a whole, so
  `EnvironmentValidator` does not include them in the unconditional production-required list.

## PostgreSQL Smoke Test

The PostgreSQL/Flyway smoke test is opt-in because it requires a reachable PostgreSQL
database with the extensions required by migrations.

```powershell
.\gradlew.bat postgresSmokeTest
```

Set these variables when the defaults do not match your local or CI database:

| Variable | Purpose |
| --- | --- |
| `POSTGRES_SMOKE_DATASOURCE_URL` | JDBC URL for the smoke-test PostgreSQL database |
| `POSTGRES_SMOKE_DATASOURCE_USERNAME` | Smoke-test database user |
| `POSTGRES_SMOKE_DATASOURCE_PASSWORD` | Smoke-test database password |

## Local Development

Local development should prefer shell environment variables, IDE run configuration
variables, or ignored local config files. Avoid adding personal credentials to tracked
YAML files.

## Validation

When the `prod` profile is active, `EnvironmentValidator` checks the required variables
above when Spring publishes `ApplicationReadyEvent`. Missing or blank required variables
fail that ready event and close the application context, but this is not a pre-bind validation
guarantee; deployment health checks must gate production traffic. GitHub, Google, and Discord
are all configured production providers, so no provider credentials are optional.
`EnvironmentValidatorTest` keeps this required table synchronized with the validator.
