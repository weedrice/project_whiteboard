# Docker Compose Local Setup

This compose file runs the Spring Boot backend and built Vue frontend. The backend connects to a PostgreSQL database
outside the Compose stack, defaulting to `host.docker.internal:5432`.

## Required Secret

`JWT_SECRET_DEV` must be provided from your shell or an untracked `.env` file. Use a Base64-encoded HS256 key.
Compose passes this value to the backend container and fails during configuration if it is missing.

Do not commit `.env`.

## Start The Stack

```bash
docker compose up -d --build
```

Default URLs:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
Database: host.docker.internal:5432
```

The PostgreSQL database must already exist and must provide the extensions required by migrations, including `pg_trgm`
and `vector`. Override `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` when
your local database is not reachable through the defaults.

To run a repository-managed PostgreSQL 16 with pgvector instead, use the optional override:

```bash
docker compose -f docker-compose.yml -f docker-compose.local-db.yml up -d --build
```

The override uses the named volume `noviis-postgres-data`; the default compose behavior remains connected to an external database.

## Defaults And Overrides

Set shell variables or create an untracked `.env` file before running compose:

```text
POSTGRES_DB=whiteboard
POSTGRES_HOST=host.docker.internal
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password
POSTGRES_PORT=5432
JWT_SECRET_DEV=<base64-encoded-hs256-key>
BACKEND_PORT=8080
FRONTEND_PORT=5173
APP_FRONTEND_URL=http://localhost:5173
APP_THUMBNAIL_ALLOWED_EXTERNAL_HOSTS=noviis.kr,www.noviis.kr,cdn.noviis.kr
VITE_API_URL=/api/v1
VITE_INQUIRY_BOARD_URL=inquiry
MANAGEMENT_HEALTH_MAIL_ENABLED=false
```

`APP_THUMBNAIL_ALLOWED_EXTERNAL_HOSTS` accepts comma-separated exact HTTPS host names.
When `APP_FRONTEND_URL` is changed, retain any other public frontend aliases that may still appear in stored post HTML.

For local development, AWS and mail-related variables default to local placeholder values unless overridden. Mail health is disabled by default because the compose stack does not include an SMTP server. Real credentials should be supplied only through local shell variables, untracked `.env`, or deployment secrets.

## Useful Commands

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
docker compose down
```

`docker compose down -v` removes Compose-managed volumes only. It does not delete the external PostgreSQL database used
by this stack.
