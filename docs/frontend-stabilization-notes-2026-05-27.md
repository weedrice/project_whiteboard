# Frontend Stabilization Notes - 2026-05-27

## Summary

Frontend stability checks are split into two layers:

- `frontend/scripts/smoke-local.mjs` remains the lightweight HTTP/API smoke check.
- Codex Browser checks remain a manual verification checklist for interactive UI flows.

No browser automation dependency is currently added to the project.

## Completed

- `UserSelectModal` uses `BaseTable` with compact density, no table shadow, row-click selection, optional selection/email columns, and the existing `max-h-[420px]` scroll region.
- `BaseTable` scan found no immediate raw table conversion candidates. The only raw `<table>` under `frontend/src` is the internal table rendered by `BaseTable`.
- Admin user/config/error-log/inquiry/report/IP block tables, board post list, and `UserSelectModal` are already commonized through `BaseTable`.

## Current Smoke Coverage

`smoke-local.mjs` verifies:

- SPA shell responses include `id="app"`.
- Protected routes do not unexpectedly redirect to `/auth/login`.
- Login succeeds without printing tokens or response bodies.
- Authenticated API routes return 2xx responses with the Bearer token and local `Origin`/`Referer` headers.

Codex Browser checklist:

- `/admin/boards`: log in as `admin / password`, open the manager selection modal, select a row, verify the save button becomes enabled, then close the modal.
- `/board/{boardUrl}/edit`: open the manager modal, verify the compact `BaseTable` renders, and verify the board-manager source does not show the email column.
- `/mypage/recent`, `/admin/inquiries`, `/admin/error-logs`: verify the route renders content, does not show the login form, and does not show an error page.

## Deferred

- `PostForm` video URL popover tests still use positional selectors such as `.video-url-popover-actions button:last-child`; keep this as a focused follow-up because the spec is large.
- Full E2E adoption is deferred. Benefits: realistic user-flow coverage for login/admin/user journeys. Costs: new tooling, environment setup, fixture data, and slower CI.
- Browser smoke script automation is deferred until the project explicitly accepts a browser automation dependency or a repo-local browser runner pattern.

## Next Candidates

- Continue selector cleanup in the large board editor specs, starting with `PostForm` video popover actions.
- Evaluate a small E2E proof of concept only after the team agrees on tooling, fixture strategy, and CI runtime budget.
- Keep `smoke-local.mjs` focused on fast HTTP/API checks unless a browser runner is added intentionally.
