ARG NODE_IMAGE=node:24.22.3-alpine3.24@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5
ARG NGINX_IMAGE=nginx:1.30.7-alpine3.24-slim@sha256:8c950211a641ec5b23f265450517001f6bcca533666120ebe36cb4cff3d00a55

FROM ${NODE_IMAGE} AS build

# Keep the build environment's system zlib patched as well as the served image.
RUN --mount=type=secret,id=build_ca \
    if [ -s /run/secrets/build_ca ]; then export SSL_CERT_FILE=/run/secrets/build_ca; fi; \
    apk add --no-cache 'zlib>=1.4-r0'

WORKDIR /app

ARG VITE_API_URL=/api/v1
ARG VITE_INQUIRY_BOARD_URL=inquiry
ARG VITE_COMMIT_HASH=docker

ENV VITE_API_URL=${VITE_API_URL}
ENV VITE_INQUIRY_BOARD_URL=${VITE_INQUIRY_BOARD_URL}
ENV VITE_COMMIT_HASH=${VITE_COMMIT_HASH}

COPY package*.json ./
COPY scripts/patch-picomatch.mjs ./scripts/
RUN --mount=type=secret,id=build_ca \
    if [ -s /run/secrets/build_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/build_ca; fi; \
    npm ci --no-audit --no-fund

COPY . .
RUN npm run build

FROM ${NGINX_IMAGE}

# Enforce the zlib security baseline (CVE-2026-4738) independently of the base image.
RUN --mount=type=secret,id=build_ca \
    if [ -s /run/secrets/build_ca ]; then export SSL_CERT_FILE=/run/secrets/build_ca; fi; \
    apk add --no-cache 'zlib>=1.4-r0'

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
RUN touch /var/run/nginx.pid \
    && chown -R nginx:nginx /var/cache/nginx /usr/share/nginx/html \
    && chown nginx:nginx /var/run/nginx.pid

EXPOSE 8080

USER nginx
