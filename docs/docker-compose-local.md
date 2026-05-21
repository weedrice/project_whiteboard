# Docker Compose Local Setup

This compose file runs the local PostgreSQL database with pgvector, Spring Boot backend, and built Vue frontend.

## Required Secret

`JWT_SECRET_DEV` must be provided from your shell or an untracked `.env` file. Use a Base64-encoded HS256 key.

Do not commit `.env`.

## Start The Stack

```bash
docker compose up -d --build
```

Default URLs:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
Database: localhost:5432
```

The backend container connects to PostgreSQL through the Compose network at `postgres:5432`.
The PostgreSQL service uses the pgvector image by default because the semantic search migration creates the `vector` extension.

## Defaults And Overrides

Set shell variables or create an untracked `.env` file before running compose:

```text
POSTGRES_DB=noviis
POSTGRES_IMAGE=pgvector/pgvector:0.8.2-pg16-trixie
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password
POSTGRES_PORT=5432
BACKEND_PORT=8080
FRONTEND_PORT=5173
APP_FRONTEND_URL=http://localhost:5173
VITE_API_URL=/api/v1
VITE_INQUIRY_BOARD_URL=inquiry
MANAGEMENT_HEALTH_MAIL_ENABLED=false
TZ=Asia/Seoul
```

For local development, AWS and mail-related variables default to local placeholder values unless overridden. Mail health is disabled by default because the compose stack does not include an SMTP server. Real credentials should be supplied only through local shell variables, untracked `.env`, or deployment secrets.

## Useful Commands

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
docker compose down
docker compose down -v
```

Use `docker compose down -v` only when you intentionally want to delete the local database volume.
