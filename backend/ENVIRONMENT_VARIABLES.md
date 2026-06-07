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
| `AGENT_INTERNAL_SECRET` | Internal agent API shared secret |
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

## Optional Or Deployment-Specific

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile. Production should include `prod`. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | Reverse proxy header handling override when needed. |
| `LOG_LEVEL` | Deployment-specific logging verbosity override when supported. |

## Local Development

Local development should prefer shell environment variables, IDE run configuration
variables, or ignored local config files. Avoid adding personal credentials to tracked
YAML files.

## Validation

When the `prod` profile is active, `EnvironmentValidator` checks the required variables
above during application startup. Missing or blank required variables fail startup before
the application begins serving traffic.
