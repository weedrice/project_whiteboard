# Auth Domain AGENTS.md

## Scope

This domain owns authentication, credentials, and token lifecycle. Apply this guidance to related user-session operations, shared security, OAuth handlers, notification invalidation, and their tests.

## Sources

- Read the shared [auth session lifecycle](../../../../../../../../../docs/design-notes/auth-session-lifecycle.md) when changing tokens, cookies, logout, refresh, or frontend session behavior.
- Use [SessionTokenService](service/SessionTokenService.java), [RefreshTokenLifecycleService](service/RefreshTokenLifecycleService.java), and [AccountCredentialInvalidationService](service/AccountCredentialInvalidationService.java) for issuance and invalidation.
- Check [UserSessionService](../user/service/UserSessionService.java), [JwtAuthenticationFilter](../../global/security/JwtAuthenticationFilter.java), and [JwtTokenProvider](../../global/security/JwtTokenProvider.java) for session identity and authenticated requests.
- Check [RefreshTokenCookieWriter](../../global/security/RefreshTokenCookieWriter.java) and [NotificationAccessInvalidationService](../notification/service/NotificationAccessInvalidationService.java) for browser and SSE boundaries.

## Invariants

- Preserve refresh-token hashing and server-side account eligibility checks. Use current server authority and security-version state when issuing access tokens.
- A successful refresh revokes the old refresh token and retains its `sessionFamilyId`; a new login creates a new family. Token-row IDs, session families, and frontend session generations are different identities.
- Keep user locking and refresh-token revalidation in the established rotation/revocation flow. Methods named `ForLockedUser` require the caller to hold the corresponding user lock.
- Replay or account-ineligibility revocation must commit even when refresh returns a business failure. Preserve the transaction outcome pattern instead of throwing inside the transaction and rolling back the revocation.
- Logout and session revocation invalidate the relevant family; account-wide invalidation uses the shared lifecycle service. Schedule notification/SSE disconnection after commit so rolled-back changes do not disconnect valid sessions.
- Determine the current session from verified authentication and matching user identity. Do not substitute a caller-supplied family ID or assume the refresh cookie is sent to user-session endpoints outside its path.
- Preserve the HttpOnly refresh-cookie contract and legacy-path clearing through the cookie writer. Do not move refresh tokens into browser-readable response fields or storage.

## Impact

- Trace login, OAuth, refresh, logout, password/credential changes, account restrictions, and user-session management when shared invalidation changes.
- Review `SecurityConfig`, JWT/authentication filters, cookie-origin checks, verified user resolution, and notification invalidation callers together when their contracts intersect.
- Check frontend bootstrap, refresh/replay coordination, cross-tab boundaries, query isolation, and SSE reconnection. Normal refresh must remain compatible with the current frontend session generation.

## Verification

Use the backend Verification command with affected domain and global-security classes:

- Rotation and persistence: `SessionTokenServiceTest`, `SessionTokenServiceRefreshPersistenceTest`, and `RefreshTokenLifecycleServiceTest`.
- Credentials and sessions: `AccountCredentialInvalidationServiceTest`, `AuthServiceTest`, and `UserSessionServiceTest`.
- HTTP/security: `AuthControllerTest`, `JwtAuthenticationFilterTest`, `JwtTokenProviderTest`, `RefreshTokenCookieWriterTest`, `AuthCookieOriginInterceptorTest`, and `OAuth2SuccessHandlerTest` as affected.
- Run affected frontend session tests for shared browser contracts. Use the persistence test for committed revocation claims; distinguish automated evidence from live cookie, OAuth, or SSE verification.
