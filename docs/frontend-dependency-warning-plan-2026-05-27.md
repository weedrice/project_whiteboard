# Frontend Dependency Warning Plan - 2026-05-27

## Summary

Frontend build currently completes. The first dependency-warning cleanup pass removed the Docker commit hash warning, refreshed Browserslist data, and applied same-major audit patches. The remaining warning class is the ESLint 8 deprecated install chain, which should be handled as a separate major migration.

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

## Major Migration Needed

- Deprecated npm install chain
  - Docker `npm ci` reports deprecated `eslint@8.57.1`, `@humanwhocodes/config-array`, `@humanwhocodes/object-schema`, `rimraf@3`, `glob@7`, and `inflight`.
  - Most of this chain is tied to ESLint 8 era dependencies.
  - Current config: `frontend/eslint.config.mjs` is already on ESLint flat config, importing `@eslint/js`, `typescript-eslint`, `eslint-plugin-vue`, `eslint-plugin-vitest`, and `vue-eslint-parser`.
  - Current direct versions: `eslint`/`@eslint/js` `8.57.1`, `typescript-eslint` `8.56.0`, `eslint-plugin-vue` `10.8.0`, `eslint-plugin-vitest` `0.5.4`, and `vue-eslint-parser` `10.4.0`.
  - Available versions checked on 2026-05-27: `eslint` latest `10.4.0`, `@eslint/js` latest `10.0.1`, `typescript-eslint` wanted/latest `8.60.0`, and `eslint-plugin-vue` wanted/latest `10.9.1`.
  - Compatibility note: `eslint-plugin-vitest@0.5.4` declares an ESLint peer range of `^8.57.0 || ^9.0.0`, while `typescript-eslint`, `eslint-plugin-vue`, and `vue-eslint-parser` already declare ranges that include ESLint 10.
  - Recommended next unit: migrate to ESLint 9 first, update the compatible parser/plugin packages in the same commit, and keep ESLint 10 as a later follow-up after `eslint-plugin-vitest` compatibility is verified or replaced.
  - Verification commands for that migration: `npm.cmd run lint:ci`, `npm.cmd run test:run`, `npm.cmd run type-check`, and `npm.cmd run build`.
  - Risk: lint output and rule behavior may change, so this should stay separate from runtime dependency cleanup.

## Deferred

- Docker image package workaround
  - Installing `git` in the frontend Docker build image would hide the warning, but it increases OS package surface.
  - Prefer environment-provided commit hash fallback first.

- Transitive audit overrides
  - Status: no longer needed after same-major audit patch cleanup.
  - Avoid broad `overrides` unless a future audit cannot be resolved by normal compatible updates.

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
