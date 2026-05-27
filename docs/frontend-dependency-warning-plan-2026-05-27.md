# Frontend Dependency Warning Plan - 2026-05-27

## Summary

Frontend build currently completes. The dependency-warning cleanup removed the Docker commit hash warning, refreshed Browserslist data, applied same-major audit patches, and migrated the frontend lint stack from ESLint 8 to ESLint 9.

Latest checked command:

```powershell
npm.cmd audit --audit-level=low
```

Result after same-major patch cleanup: 0 vulnerabilities.

## Immediately Actionable

- Docker build: `/bin/sh: git: not found`
  - Status: fixed by making `vite.config.ts` prefer `VITE_COMMIT_HASH` and only running `git rev-parse --short HEAD` as a fallback.
  - Keep avoiding `git` installation in the frontend Docker image unless commit metadata cannot be supplied another way.

- Browserslist stale database
  - Status: fixed by refreshing Browserslist data through `npx update-browserslist-db@latest`.
  - `package-lock.json` now carries the refreshed browser data, while `package.json` dependency ranges remain unchanged.

- Same-major dependency patch updates
  - Status: fixed with `npm.cmd audit fix` without `--force`.
  - `npm.cmd audit --audit-level=low` now reports 0 vulnerabilities.
  - `package.json` dependency ranges remain unchanged.

## Completed Major Migration

- Deprecated npm install chain
  - Status: fixed by migrating `eslint`/`@eslint/js` to `9.39.4`, `typescript-eslint` to `8.60.0`, and `eslint-plugin-vue` to `10.9.1`.
  - `eslint-plugin-vitest` was replaced with `@vitest/eslint-plugin` to avoid the old `@typescript-eslint/utils@7` dependency path.
  - Docker `npm ci` no longer reports deprecated `eslint@8.57.1`, `@humanwhocodes/config-array`, `@humanwhocodes/object-schema`, `rimraf@3`, `glob@7`, or `inflight`.
  - Current config: `frontend/eslint.config.mjs` is already on ESLint flat config, importing `@eslint/js`, `typescript-eslint`, `eslint-plugin-vue`, `@vitest/eslint-plugin`, and `vue-eslint-parser`.
  - Current direct versions: `eslint`/`@eslint/js` `9.39.4`, `typescript-eslint` `8.60.0`, `eslint-plugin-vue` `10.9.1`, `@vitest/eslint-plugin` `1.6.18`, and `vue-eslint-parser` `10.4.0`.

## Deferred

- Docker image package workaround
  - Installing `git` in the frontend Docker build image would hide the warning, but it increases OS package surface.
  - Prefer environment-provided commit hash fallback first.

- Transitive audit overrides
  - Status: no longer needed after same-major audit patch cleanup.
  - Avoid broad `overrides` unless a future audit cannot be resolved by normal compatible updates.

- ESLint 10
  - ESLint latest was `10.4.0` when checked on 2026-05-27.
  - Keep ESLint 10 as a later follow-up because ESLint 9 already removes the deprecated install chain and keeps the current toolchain on a conservative compatibility path.

- npm CLI major notice
  - Docker build still reports the informational npm CLI notice `10.9.8 -> 11.15.0`.
  - Do not change the Node base image or install a different npm CLI solely for this notice.

## UI Follow-Up Status

- `frontend/src/components/common/widgets/UserSelectModal.vue`
  - Status: applied after adding focused tests for search, initial selected ids, single/multiple selection, `board-manager-candidates` source, hidden email column, excluded users, loading, and empty states.
  - The modal now uses `BaseTable` with compact density, no table shadow, row click selection, optional selection/email columns, and the existing `max-h-[420px]` scroll region.

## BaseTable Follow-Up Scan

Latest scan:

```powershell
rg "<table" frontend/src -n
rg "<BaseTable" frontend/src -n
```

- Immediate conversion candidates: none. The only raw `<table>` under `frontend/src` is the internal table rendered by `BaseTable` itself.
- Already commonized: admin user/config/error-log/admin/inquiry/report/IP block tables, board post list, and `UserSelectModal` all use `BaseTable`.
- Deferred candidates: none currently identified from raw table usage. Future list or grid cleanup should start from repeated card/list markup instead of table conversion.
- Caution: keep compact modal tables on `BaseTable` only when selection, scroll height, and optional columns are covered by focused tests.

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
