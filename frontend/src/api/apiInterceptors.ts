import type { AxiosError, AxiosInstance, AxiosResponse } from 'axios'
import { applyAuthHeader } from '@/api/apiAuthHeader'
import { handleResponseError } from '@/api/apiResponseErrorHandler'

export { getCurrentPathname, isLoginPathname } from '@/api/apiAuthHeader'
export { applyAuthHeader }
export { retryAfterRefresh } from '@/api/apiRefreshRetry'
export { handleGlobalApiError, handleResponseError } from '@/api/apiResponseErrorHandler'

export function installApiInterceptors(api: AxiosInstance) {
  api.interceptors.request.use(
    applyAuthHeader,
    (error: AxiosError) => Promise.reject(error),
  )

  api.interceptors.response.use(
    (response: AxiosResponse) => response,
    (error: AxiosError) => handleResponseError(api, error),
  )
}
