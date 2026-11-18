FROM redis:7.4.12-alpine3.22@sha256:7f7d30b7fdd09467bac58198a1f166dd69789d348d4fe859cccb20e0cf287fbe

# CVE-2026-4738: the upstream image predates Alpine's zlib security update.
# Update this package only; retain the upstream Redis entrypoint and account.
RUN --mount=type=secret,id=build_ca \
    if [ -s /run/secrets/build_ca ]; then export SSL_CERT_FILE=/run/secrets/build_ca; fi; \
    apk add --no-cache 'zlib>=1.4-r0'
