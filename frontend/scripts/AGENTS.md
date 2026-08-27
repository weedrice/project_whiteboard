# Frontend Scripts AGENTS.md

## Scope And Source Of Truth

This directory contains build validation, generated API checks, SEO, sitemap, prerender, release-manifest, PWA, coverage, and local smoke scripts. Changes can affect production artifacts even when application source code is unchanged.

- Read the SEO and artifact sections of `.github/workflows/README.md` before changing sitemap, prerender, release-manifest, or production verification behavior.
- Before changing `check-generated-api.mjs` or the `api:*` generation contract, read and follow `docs/api/AGENTS.md`.
- Treat `frontend/package.json` as the command entrypoint contract. Update package scripts, workflows, fixtures, and documentation together when invocation or environment requirements change.
- Keep scripts deterministic, UTF-8, non-interactive, and fail-closed for production validation. Do not hide network, parsing, digest, count, or generated-output failures in strict mode.

## SEO Release Contract

- Keep sitemap generation, prerender input, `.noviis-seo-release.json`, and verification bound to the shared `SEO_POST_URL_CAPACITY` and the 50,000-URL protocol ceiling.
- Preserve `SEO_STRICT=true` behavior: API failure, zero post URLs, incomplete prerender output, count mismatch, invalid release SHA, or sitemap digest mismatch must fail the production release.
- Keep the release manifest fields and their consumers synchronized. Deployment and monitoring verification must continue to bind the active release SHA to the sitemap and manifest.
- Preserve safe URL and origin validation before fetching or rendering remote content. Do not broaden accepted schemes or origins without a coordinated security review.

## Validation

From `frontend/`, run a syntax check and the closest script fixture first:

```powershell
node --check .\scripts\<changed-script>.mjs
npm.cmd run test:run -- scripts/__tests__/<related-test>.spec.mjs
```

For coordinated SEO changes, run the relevant script test directory and the normal frontend type check:

```powershell
npm.cmd run test:run -- scripts/__tests__
npm.cmd run type-check
```

`npm run build:seo` requires valid build-time API and SEO environment. Do not claim the strict production build was verified unless those prerequisites were configured and the command actually ran.
