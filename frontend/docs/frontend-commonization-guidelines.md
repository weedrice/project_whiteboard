# Frontend Commonization Guidelines

## Data Layer

- Keep raw backend shape normalization in API adapters when the endpoint has known response variants.
- Composables should prefer working with domain data or `PageResponse<T>`, not raw envelope variants.
- Use `useApiQuery` for simple `useQuery -> API request -> unwrap` flows.
- Use `useApiPageQuery` or `useNullableApiPageQuery` for paginated API flows that may need `PageResponse` normalization.
- Keep specialized query functions local when the flow branches by mode, performs optimistic updates, or has unusual cache behavior.

## Query Keys

- Query keys with params should be value-based computed keys, not keys containing mutable `Ref` objects directly.
- Each domain should expose stable root keys for invalidation, such as `usersRoot`, `reportsRoot`, or `postsRoot`.
- Prefer domain-specific invalidation helpers when a mutation must refresh several related roots.

## UI Layer

- Prefer existing base components before adding new wrappers: `Base*`, `PaginatedListCard`, `AdminDataPage`, `AdminPaginatedTable`, and `AdminDetailModalShell`.
- Extract shared UI only when the shared part can own layout and state boundaries without hiding domain-specific behavior.
- Keep body rendering in slots for sections that differ by domain, such as dashboard posts, dashboard comments, or admin detail contents.
- Add variants to an existing component only when the variant uses the same semantic component, such as `BoardCard` compact results.
