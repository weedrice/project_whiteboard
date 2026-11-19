# Docker Compose Local Setup

The default `docker-compose.yml` runs the Spring Boot backend, built Vue frontend, and Redis 7.4. The backend connects
to a PostgreSQL database outside the Compose stack, defaulting to `host.docker.internal:5432`. Redis provides the shop
sale-status stream relay; the backend starts after Redis is healthy, and the frontend starts after the backend is healthy.

Container versions and digests are pinned in the Dockerfiles and Compose files. The security baseline is Node 24.22.3,
Zulu CA 25.0.5.1+2, Nginx 1.30.7 on Alpine 3.24 slim, Redis 7.4.12 on Alpine 3.22, and pgvector 0.8.7 with PostgreSQL 16
on Debian Bookworm. The Nginx slim image omits unused image/XSLT modules while preserving static files and reverse proxy
behavior. PostgreSQL stays on the existing major version and Bookworm base to avoid an implicit data or locale migration.

Updating these pins does not restart existing containers or upgrade an installed database extension. Back up the local
database before an intentional recreate; check `SELECT version()` and `SELECT extversion FROM pg_extension WHERE extname = 'vector'`
afterward. Existing vector installations retain their extension version until a separately reviewed `ALTER EXTENSION vector UPDATE`
is run. Do not remove the PostgreSQL volume to apply an image update.

## Required Secret

`JWT_SECRET_DEV` must be provided from your shell or an untracked `.env` file. Use a Base64-encoded HS256 key.
Compose passes this value to the backend container and fails during configuration if it is missing.

Do not commit `.env`.

## Start The Stack

Run these Docker Compose commands from the repository root in Windows PowerShell or a macOS/Linux shell.
Docker with Compose support must be running, and the required JWT secret and database must be available.

```powershell
docker compose up -d --build
```

Default URLs:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
Database: host.docker.internal:5432
```

Redis is reachable only inside the Compose network at `redis:6379`; no host Redis port is published. The backend
management health check uses container loopback port 8081, which is also not published to the host.

Redis starts directly as its image user (`999:1000`) with all Linux capabilities dropped. Its `/data` tmpfs belongs
to that same user and group with mode `0700`, so startup and snapshot writes do not require root or ownership changes.
The local PostgreSQL override inherits this Redis configuration unchanged.

The PostgreSQL database must already exist and must provide the extensions required by migrations, including `pg_trgm`
and `vector`. Override `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` when
your local database is not reachable through the defaults.

To run a repository-managed PostgreSQL 16 with pgvector instead, use the optional override:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local-db.yml up -d --build
```

The override uses the Compose volume key `noviis-postgres-data` and connects the backend to `postgres:5432`; it does
not publish a PostgreSQL host port. The default compose behavior remains connected to an external database.
Use both `-f` arguments for subsequent `ps`, `logs`, and `down` commands when managing the override stack.

## Defaults And Overrides

Set shell variables or create an untracked `.env` file before running compose:

```text
POSTGRES_DB=whiteboard
POSTGRES_HOST=host.docker.internal
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<local-database-password>
POSTGRES_PORT=5432
JWT_SECRET_DEV=<base64-encoded-hs256-key>
BACKEND_PORT=8080
FRONTEND_PORT=5173
APP_FRONTEND_URL=http://localhost:5173
APP_THUMBNAIL_ALLOWED_EXTERNAL_HOSTS=noviis.kr,www.noviis.kr,cdn.noviis.kr
VITE_API_URL=/api/v1
VITE_INQUIRY_BOARD_URL=inquiry
MANAGEMENT_HEALTH_MAIL_ENABLED=false
APP_INSTANCE_ID=noviis-backend-local
```

`APP_THUMBNAIL_ALLOWED_EXTERNAL_HOSTS` accepts comma-separated exact HTTPS host names.
When `APP_FRONTEND_URL` is changed, retain any other public frontend aliases that may still appear in stored post HTML.

For local development, AWS and mail-related variables default to local placeholder values unless overridden. Mail health is disabled by default because the compose stack does not include an SMTP server. Real credentials should be supplied only through local shell variables, untracked `.env`, or deployment secrets.

## Useful Commands

```powershell
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
docker compose down
```

`docker compose down` preserves the optional PostgreSQL named volume and the host bind mounts `uploads/` and
`logs/backend/`. Redis stores `/data` in tmpfs, so its data is ephemeral. Adding `-v` when stopping the override stack
deletes its PostgreSQL named volume and database data; use that destructive option only when explicitly intending to
reset that local database. Neither variant deletes an external PostgreSQL database or the host bind-mounted files.

Redis is built from `deploy/docker/redis/Dockerfile` as `noviis-redis:local`. The pinned upstream Redis image still contains zlib 1.3.2-r1, so the derivative installs only `zlib>=1.4-r0` from its existing Alpine 3.22 repository (CVE-2026-4738). The minimum permits later vendor security revisions without a full OS upgrade. Rebuild with the normal `docker compose build` flow before recreating services. CI verifies the image under the same UID, read-only filesystem, dropped capabilities, and tmpfs data contract. Existing Redis volumes are unchanged.

Dependabot monitors the Alpine and Redis Docker base pins. Azul archive versions and SHA-256 values must be updated together from the official metadata API; Docker Dependabot does not update URLs embedded in RUN instructions. Keep Java25, CI setup-java, and native host prerequisites synchronized.

For a local enterprise TLS interception environment, backend/frontend/Redis Dockerfiles accept the optional BuildKit `build_ca` secret containing a trusted public PEM CA bundle. Use `docker build --secret id=build_ca,src=/path/to/trusted-ca.pem ...`; trust applies only to download RUN steps and the certificate bundle is not copied into the final image. Standard CI omits the secret. TLS verification and archive checksums stay enabled.

The Nginx runtime likewise installs only `zlib>=1.4-r0` after the pinned upstream image because the final rescan detected the same older library. The backend Alpine runtime also declares this minimum.
