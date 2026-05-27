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

## UI Follow-Up Candidate

- `frontend/src/components/common/widgets/UserSelectModal.vue`
  - The compact modal table is still intentionally manual.
  - It combines single and multi-select modes, row click selection, optional email column, board-manager candidate source, current-manager badge display, and a fixed `max-h-[420px]` scroll region inside `BaseModal`.
  - A direct `BaseTable` conversion could change modal height, row hit targets, selected-row highlighting, or checkbox-like affordance behavior.
  - Recommended follow-up: add focused tests around search, initial selected ids, single/multiple selection, `board-manager-candidates` source, hidden email column, and excluded users before attempting any table abstraction.
  - Only convert this table if `BaseTable` can support compact density, scroll containment, row selection styling, and optional leading selection cells without introducing nested-card or modal spacing changes.

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
