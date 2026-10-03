import type { AxiosError, AxiosInstance, AxiosResponse } from 'axios'
import { applyAuthHeader } from '@/api/apiAuthHeader'
import { handleResponseError } from '@/api/apiResponseErrorHandler'
import { applyApiLocaleHeader } from '@/api/apiLocaleHeader'
import { withExpectedSession } from '@/api/apiExpectedSession'

export { getCurrentPathname, isLoginPathname } from '@/api/apiAuthHeader'
export { applyAuthHeader }
export { retryAfterRefresh, resetAuthRefreshFailureCooldownForTest } from '@/api/apiRefreshRetry'
export { handleGlobalApiError, handleResponseError } from '@/api/apiResponseErrorHandler'

export function installApiInterceptors(api: AxiosInstance) {
  api.interceptors.request.use(
    (config) => withExpectedSession(config, () => applyApiLocaleHeader(applyAuthHeader(config))),
    (error: AxiosError) => Promise.reject(error),
  )

  api.interceptors.response.use(
    (response: AxiosResponse) => withExpectedSession(response.config, () => response),
    (error: AxiosError) => handleResponseError(api, error),
  )
}
