# Frontend Scripts AGENTS.md

## Scope And Source Of Truth

This directory owns build validation, generated API checks, SEO, sitemap, prerender, release-manifest, PWA, coverage, and local smoke scripts. It inherits [frontend/AGENTS.md](../AGENTS.md); script changes can affect production artifacts without application source changes.

- Read the [workflow release/SEO contract](../../.github/workflows/README.md) and [static-content policy](../../docs/ops/seo-static-content-policy.md) before changing sitemap, prerender, release-manifest, or production verification behavior.
- Before changing `check-generated-api.mjs` or the `api:*` contract, follow [API generation instructions](../../docs/api/AGENTS.md).
- [package.json](../package.json) owns command entrypoints. Keep package scripts, workflows, fixtures, and documentation aligned when invocation or environment requirements change.
- Keep scripts deterministic, non-interactive, and fail-closed for production validation. Do not hide network, parsing, digest, count, or generated-output failures in strict mode.

## SEO Release Contract

- Keep sitemap generation, prerender input, `.noviis-seo-release.json`, and verification bound to `SEO_POST_URL_CAPACITY` and the protocol ceiling defined in [seo-capacity.mjs](seo-capacity.mjs); do not duplicate those limits.
- Preserve `SEO_STRICT=true` behavior: API failure, zero post URLs, incomplete prerender output, count mismatch, invalid release SHA, or sitemap digest mismatch must fail the production release.
- Keep the release manifest fields and their consumers synchronized. Deployment and monitoring verification must continue to bind the active release SHA to the sitemap and manifest.
- Preserve safe URL and origin validation before fetching or rendering remote content. Do not broaden accepted schemes or origins without a coordinated security review.

## Validation

From `frontend/`, run a syntax check and the closest script fixture first:

```powershell
node --check .\scripts\<changed-script>.mjs
npm.cmd run test:run -- scripts/__tests__/<related-test>.spec.mjs
```

For coordinated SEO changes, expand to `npm.cmd run test:run -- scripts/__tests__` and `npm.cmd run type-check`.

`npm.cmd run build:seo` requires the build-time API and SEO environment described in the workflow contract. Claim strict production verification only when `SEO_STRICT=true` and those prerequisites were configured and the command actually ran.
