export const ACCESS_TOKEN_KEY = 'accessToken'
export const LEGACY_REFRESH_TOKEN_KEY = 'refreshToken'
export const AUTH_SESSION_EVENT_KEY = 'authSessionEvent'

let inMemoryAccessToken: string | null = null

export function getStoredAccessToken(): string | null {
    return inMemoryAccessToken
}

export function persistAccessToken(token: string): void {
    inMemoryAccessToken = token
    removeLegacyAuthTokens()
}

export function clearStoredAuthTokens(): void {
    inMemoryAccessToken = null
    removeLegacyAuthTokens()
    broadcastAuthSessionCleared()
}

function removeLegacyAuthTokens(): void {
    try {
        localStorage.removeItem(ACCESS_TOKEN_KEY)
        localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY)
    } catch {
        // Ignore blocked storage; access tokens are memory-only.
    }
}

function broadcastAuthSessionCleared(): void {
    try {
        localStorage.setItem(AUTH_SESSION_EVENT_KEY, String(Date.now()))
    } catch {
        // Cross-tab logout sync is best-effort.
    }
}
