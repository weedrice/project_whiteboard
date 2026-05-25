import { Storage } from '@/utils/storage'

const ACCESS_TOKEN_KEY = 'accessToken'
const LEGACY_REFRESH_TOKEN_KEY = 'refreshToken'

export function getStoredAccessToken(): string | null {
    return Storage.getString(ACCESS_TOKEN_KEY)
}

export function persistAccessToken(token: string): void {
    Storage.setString(ACCESS_TOKEN_KEY, token)
    Storage.remove(LEGACY_REFRESH_TOKEN_KEY)
}

export function clearStoredAuthTokens(): void {
    Storage.remove(ACCESS_TOKEN_KEY)
    Storage.remove(LEGACY_REFRESH_TOKEN_KEY)
}
