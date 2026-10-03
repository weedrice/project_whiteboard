# Frontend Feature Slices AGENTS.md

## Scope And Ownership

Follow [frontend/AGENTS.md](../../AGENTS.md) for ownership, shared helpers, imports, API, authentication, and UI rules. This file adds feature-specific boundaries.

- Use the closest existing slice: [board/posts](board/posts) owns post detail, draft, editor, form, and query helpers; [emoticon](emoticon) owns detail, form, list, and picker helpers.

## Public Contracts And Dependencies

- Keep one authoritative query-key factory and invalidation contract for each domain. Other features and router/SSE integrations may import those public contracts directly.
- Do not depend on another feature's private component, mutable form state, or implementation-only helper to implement a cross-feature workflow. Use an explicit domain contract or an existing shared API/type boundary.
- When changing a public contract, inspect consumers outside this slice as well as local code: views, components, router guards, and notification handlers can share its cache keys.

## Verification

Use the parent's [verification matrix](../../AGENTS.md#verification), including its session/cache integration requirements, for the local feature and affected external consumers.
