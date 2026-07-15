# Frontend Commonization Guidelines

## Feature Boundaries

- Put domain-specific orchestration under `src/features/{domain}` instead of adding new implementation logic to root `src/composables`.
- Current top-level feature slices are `admin`, `board`, `comments`, `emoticon`, `feed`, `mentions`, `notifications`, `shop`, and `user`; `board/posts` is a nested post slice under `board`.
- Feature slices may be adopted incrementally; the presence of a domain directory does not mean every root composable for that domain has already moved.
- `src/composables` should hold shared cross-feature helpers, low-level reusable behavior, query key helpers that are still intentionally global, or thin compatibility re-export shims.
- When moving existing composables, keep a root shim if older imports, tests, or gradual migration require it. New consumers should import from `src/features/...`.
- Prefer feature-local test helpers and harnesses when a spec grows around one route or component workflow.

## Data Layer

- Keep raw backend shape normalization in API adapters when the endpoint has known response variants.
- Feature composables should prefer working with domain data or `PageResponse<T>`, not raw envelope variants.
- Use `useApiQuery` for simple `useQuery -> API request -> unwrap` flows.
- Use `useApiPageQuery` or `useNullableApiPageQuery` for paginated API flows that may need `PageResponse` normalization.
- Keep specialized query functions local when the flow branches by mode, performs optimistic updates, or has unusual cache behavior.

## Query Keys

- Query keys with params should be value-based computed keys, not keys containing mutable `Ref` objects directly.
- Each domain should expose stable root keys for invalidation, such as `usersRoot`, `reportsRoot`, or `postsRoot`.
- Prefer domain-specific invalidation helpers when a mutation must refresh several related roots.
- Keep query keys and invalidation helpers close to the feature that owns the data unless multiple unrelated features intentionally share them.

## UI Layer

- Prefer existing base components before adding new wrappers: `Base*`, `PaginatedListCard`, `AdminDataPage`, `AdminPaginatedTable`, and `AdminDetailModalShell`.
- Extract shared UI only when the shared part can own layout and state boundaries without hiding domain-specific behavior.
- Keep body rendering in slots for sections that differ by domain, such as dashboard posts, dashboard comments, or admin detail contents.
- Add variants to an existing component only when the variant uses the same semantic component, such as `BoardCard` compact results.

## Test Layer

- When a component spec accumulates repeated mount setup, stubs, or mutation fixtures, move those details into a local `*TestHarness.ts`.
- Keep the spec file focused on user-visible behavior and assertions; keep setup mechanics in the harness.
- Reuse shared fixtures only when they are truly cross-domain. Domain-specific fixtures should live near the feature or component tests that use them.
