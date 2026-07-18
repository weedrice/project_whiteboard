import type { InternalAxiosRequestConfig } from 'axios'
import { API_PATHS } from '@/api/apiPaths'
import { getRequestPathname } from '@/api/apiUrl'
import { getStoredAccessToken } from '@/utils/authTokenStorage'
import { getCurrentSessionGeneration } from '@/queryAuthScope'
import { getCurrentPathname as readCurrentPathname } from '@/utils/browserEnv'

export const getCurrentPathname = readCurrentPathname

export const isLoginPathname = (pathname = getCurrentPathname()): boolean => pathname === API_PATHS.LOGIN

export function applyAuthHeader(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const token = getStoredAccessToken()
  config._authSessionGeneration = getCurrentSessionGeneration()
  config._authAccessToken = token
  const pathname = getRequestPathname(config.url)
  const isAuthEndpoint = pathname.startsWith('/auth/')
  const isEmailVerificationApi = pathname === '/auth/email/send-verification' || pathname === '/auth/email/verify'

  if (token && (!isAuthEndpoint || isEmailVerificationApi) && !config.preserveAuthHeader) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}

export function isStampedAuthContextCurrent(
  config: InternalAxiosRequestConfig,
  sessionGeneration: number,
  accessToken: string | null,
) {
  if (config._authSessionGeneration === undefined) return false
  return config._authSessionGeneration === sessionGeneration
    && config._authAccessToken === accessToken
}
