import type { InternalAxiosRequestConfig } from 'axios'
import { API_PATHS } from '@/api/apiPaths'
import { getRequestPathname } from '@/api/apiUrl'
import { getStoredAccessToken } from '@/utils/authTokenStorage'
import { getCurrentPathname as readCurrentPathname } from '@/utils/browserEnv'

export const getCurrentPathname = readCurrentPathname

export const isLoginPathname = (pathname = getCurrentPathname()): boolean => pathname === API_PATHS.LOGIN

export function applyAuthHeader(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const token = getStoredAccessToken()
  const pathname = getRequestPathname(config.url)
  const isAuthEndpoint = pathname.startsWith('/auth/')
  const isEmailVerificationApi = pathname === '/auth/email/send-verification' || pathname === '/auth/email/verify'

  if (token && (!isAuthEndpoint || isEmailVerificationApi)) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}
