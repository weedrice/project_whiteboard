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
| `APP_CACHE_READ_OPTIMIZATION_ENABLED` | Enables the application read-optimization cache path. Defaults to `false`. |
| `APP_ERROR_LOG_CLIENT_RETENTION_DAYS` | Client error-log retention period. Defaults to `30` days. |
| `APP_ERROR_LOG_RESOLVED_RETENTION_DAYS` | Resolved error-log retention period. Defaults to `90` days. |
| `APP_ERROR_LOG_CLEANUP_BATCH_SIZE` | Maximum error-log cleanup batch size. Defaults to `500`. |
| `SEMANTIC_SEARCH_RELATED_POST_MIN_SIMILARITY` | Minimum similarity for related posts. Defaults to `0.55`. |
| `DB_MAX_POOL_SIZE` | Production Hikari maximum pool size override. Defaults to `20`. |
| `DB_MIN_IDLE` | Production Hikari minimum idle connection override. Defaults to `5`. |
| `RATE_LIMIT_AUTH_ACCOUNT_LIMIT` | Per-account auth rate-limit capacity override. |
| `RATE_LIMIT_BUCKET_CACHE_MAX_SIZE` | Maximum rate-limit bucket cache size override. |
| `RATE_LIMIT_BUCKET_CACHE_TTL_MINUTES` | Rate-limit bucket cache time-to-live override. |
| `CLIENT_IP_TRUST_PROXY_HEADERS` | Enables trusted proxy header parsing. Defaults to `true` in prod. |
| `CLIENT_IP_TRUSTED_PROXIES` | Comma-separated trusted proxy IP/CIDR list. |

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
above during application startup. Missing or blank required variables fail startup before
the application begins serving traffic.
