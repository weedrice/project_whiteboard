# Frontend Dependency Warning Plan - 2026-05-27

## Summary

Frontend build currently completes, but local Docker/npm output repeatedly reports dependency maintenance warnings. This note classifies the warnings only; it does not change `package.json`, `package-lock.json`, Docker images, or runtime dependencies.

Latest checked command:

```powershell
npm.cmd audit --audit-level=low
```

Result: 16 vulnerabilities, including 1 low, 7 moderate, and 8 high.

## Immediately Actionable

- Docker build: `/bin/sh: git: not found`
  - Likely cause: the Vite build path invokes `git rev-parse --short HEAD` when Docker build context does not include a git binary.
  - Preferred fix: make `vite.config.ts` prefer `VITE_COMMIT_HASH` first, then run `git` only as a fallback.
  - Avoid adding `git` to the production image unless commit metadata cannot be supplied another way.

- Browserslist stale database
  - Warning: `caniuse-lite` browser data is stale.
  - Preferred fix: run a lockfile-only Browserslist database refresh in a dedicated maintenance change, then verify build and smoke.

- Same-major dependency patch updates
  - Audit currently flags direct or near-direct frontend tooling/runtime packages including `axios`, `dompurify`, `@unhead/vue`/`unhead`, `postcss`, `vite`, and `rollup`.
  - Preferred fix: update within compatible major ranges first, then run `npm audit`, `npm run lint:ci`, `npm run test:run`, and `npm run build`.

## Major Migration Needed

- Deprecated npm install chain
  - Docker `npm ci` reports deprecated `eslint@8.57.1`, `@humanwhocodes/config-array`, `@humanwhocodes/object-schema`, `rimraf@3`, `glob@7`, and `inflight`.
  - Most of this chain is tied to ESLint 8 era dependencies.
  - Preferred fix: plan an ESLint major migration with compatible versions of `@eslint/js`, `typescript-eslint`, `eslint-plugin-vue`, `eslint-plugin-vitest`, and `vue-eslint-parser`.
  - Risk: lint output and rule behavior may change, so this should be a separate migration commit/PR.

## Deferred

- Docker image package workaround
  - Installing `git` in the frontend Docker build image would hide the warning, but it increases OS package surface.
  - Prefer environment-provided commit hash fallback first.

- Transitive audit overrides
  - Audit also flags transitive packages such as `ajv`, `brace-expansion`, `flatted`, `follow-redirects`, `js-cookie`, `minimatch`, `picomatch`, and `ws`.
  - Avoid broad `overrides` unless a same-major update path cannot resolve them.
  - Recheck after direct dependency patch updates and ESLint migration planning.

## Verification For Future Cleanup

After any dependency cleanup, run:

```powershell
npm.cmd audit --audit-level=low
npm.cmd run lint:ci
npm.cmd run test:run
npm.cmd run build
docker compose build frontend
docker compose up -d frontend
$env:SMOKE_PASSWORD='password'; npm.cmd run smoke:local; Remove-Item Env:SMOKE_PASSWORD
```
