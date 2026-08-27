# Frontend Feature Slices AGENTS.md

## Scope

This directory owns domain-specific frontend orchestration, feature-local composables, query keys, cache invalidation, form state, and page resources. Repository-wide and frontend module instructions remain in force.

## Feature Boundaries

- Current top-level slices are `admin`, `board`, `comments`, `emoticon`, `feed`, `mentions`, `notifications`, `search`, `shop`, and `user`; `board/posts` is the nested post slice under `board`.
- Feature adoption is incremental. The presence of a slice does not mean every legacy root composable for that domain has already moved, so do not perform unrelated mass migration.
- `board/posts` owns post detail, draft, editor, form, and post query helpers.
- `emoticon` owns detail, form, list, and picker helpers.
- Keep feature-specific code in the closest existing slice. Put a helper in root `src/composables` only when it is genuinely reusable across features.
- Use the shared API client and typed API modules; do not create a feature-local Axios client or bypass centralized authentication, error normalization, and global toast behavior.
- Keep server state in Vue Query and shared application state in Pinia. Preserve query-key ownership and invalidate every affected cache after mutations.
- Avoid direct imports between unrelated feature internals. Promote truly shared behavior to an existing shared API, type, component, utility, or composable boundary.

## Contracts And Testing

- When a backend DTO, validation rule, error response, or endpoint changes, update the affected API types, feature orchestration, and UI consumers together.
- Add or update tests next to the affected slice in its existing `__tests__` location.
- Run the narrowest related Vitest target first, then `npm run type-check`; run broader frontend checks in proportion to the change.
