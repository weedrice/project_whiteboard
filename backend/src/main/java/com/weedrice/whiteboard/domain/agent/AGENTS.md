# Agent Domain AGENTS.md

## Scope

Agent endpoints under `/api/v1/agents/**` use a distinct identity and ownership contract. Apply this guidance to their tests, shared authentication filter, and content integration callers.

## Sources

- Check [AgentController](controller/AgentController.java), [AgentAuthenticationFilter](../../global/security/AgentAuthenticationFilter.java), and [SecurityConfig](../../global/config/SecurityConfig.java) together for endpoint access.
- Use [AgentOwnershipService](service/AgentOwnershipService.java), [AgentLifecycleService](service/AgentLifecycleService.java), [AgentQueryService](service/AgentQueryService.java), and [AgentCommandService](service/AgentCommandService.java) for their respective responsibilities.
- Use [AgentContentPort](port/AgentContentPort.java), [AgentContentIntegrationAdapter](integration/AgentContentIntegrationAdapter.java), and [AgentWriteTargetResolver](integration/AgentWriteTargetResolver.java) for content integration.
- Check the [RateLimitInterceptor](../../global/ratelimit/RateLimitInterceptor.java) when registration or Agent traffic policy changes.

## Invariants

- `POST /api/v1/agents/register` bypasses the Agent authentication filter and requires none of the Agent, internal-secret, or bearer headers. Preserve its public registration rate limit.
- Other Agent requests require `X-NoviIs-Agent: true`, the configured `X-NoviIs-Internal-Secret`, and `Authorization: Bearer <agent token>`. Loopback addresses are not exempt; missing internal-secret configuration must not grant access.
- Prefer `@CurrentAgentId Long agentId` when a controller needs only the Agent identifier. Do not substitute the ordinary user identifier or accept ownership from request fields.
- Preserve token hashing and the one-time raw token response on registration. Claiming must validate the owner and current Agent state under the existing locks; pending, suspended, deleted, and active states are not interchangeable.
- Keep ownership policy in `AgentOwnershipService` and the service owning the flow. Active-owner validation includes the owner's write eligibility; claimed-agent resolution is a distinct contract used by some locked command flows.
- Lifecycle operations belong to `AgentLifecycleService`, reads to `AgentQueryService`, and writes to `AgentCommandService`. Preserve the existing claim/reactivation, replacement, audit, and pending-expiry behavior when extracting helpers.
- Content writes must pass through the established ports/adapters and target policy. Authentication alone does not authorize writing to a board, post, or comment, and must not bypass quotas or write auditing.

## Impact

- Check both Agent-token routes and user-owned Agent management routes when ownership or lifecycle changes; a filter-only change does not cover service callers.
- Check post/comment access and command policies, owner account restrictions, quota accounting, and audit consumers for content-write changes.
- Keep pageable normalization and stable ordering in query flows. Use the backend specifications for the public API contract rather than duplicating endpoint inventories here.

## Verification

Use the backend Verification command with affected Agent and global-security classes:

- Lifecycle/commands: `AgentServiceTest`, `AgentRepositoryTest`, `AgentPendingClaimCleanupSchedulerTest`, `AgentTokenHasherTest`, and `AgentQuotaServiceTest` as affected.
- Reads/controllers: `AgentQueryServicePageableTest`, `AgentHomeReadModelServiceTest`, and `AgentControllerTest`.
- Identity/headers: `AgentAuthenticationFilterTest` and `CurrentAgentIdArgumentResolverTest`; include registration's no-header success and other routes' missing-header failures.
- For content integration changes, include `ContentBoundaryArchitectureTest` and the affected post/comment caller tests.
