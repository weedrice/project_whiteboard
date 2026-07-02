import type { InternalAxiosRequestConfig } from 'axios'
import { API_PATHS } from '@/api/apiPaths'
import { Storage } from '@/utils/storage'
import { getCurrentPathname as readCurrentPathname } from '@/utils/browserEnv'

export const getCurrentPathname = readCurrentPathname

export const isLoginPathname = (pathname = getCurrentPathname()): boolean => pathname === API_PATHS.LOGIN

export function applyAuthHeader(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const token = Storage.getString('accessToken')
  const isAuthEndpoint = config.url?.includes('/auth/')
  const isEmailVerificationApi = config.url?.includes('/auth/email/send-verification') || config.url?.includes('/auth/email/verify')

  if (token && (!isAuthEndpoint || isEmailVerificationApi)) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}
