import type { AxiosError, AxiosInstance } from 'axios'
import i18n from '@/i18n'
import router from '@/router'
import { retryAfterRefresh } from '@/api/apiRefreshRetry'
import {
  handleApiError,
  isCanceledRequestError,
  markGlobalErrorToastHandled,
  shouldMarkGlobalErrorToastHandled,
} from '@/api/errorHandling'
import {
  noopToastStore,
  resolveToastStore,
} from '@/api/apiStoreResolvers'

const { t } = i18n.global

export function handleGlobalApiError(error: AxiosError, toastStore = noopToastStore) {
  handleApiError(error, toastStore, t)
  if (shouldMarkGlobalErrorToastHandled(error, toastStore, noopToastStore)) {
    markGlobalErrorToastHandled(error)
  }
}

export async function handleResponseError(api: AxiosInstance, error: AxiosError) {
  const originalRequest = error.config
  const toastStore = await resolveToastStore()

  if (isCanceledRequestError(error)) {
    return Promise.reject(error)
  }

  if (!originalRequest) {
    handleGlobalApiError(error, toastStore)
    return Promise.reject(error)
  }

  if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.skipAuthRefresh) {
    return retryAfterRefresh(api, originalRequest)
  }

  if (originalRequest.redirectOnError) {
    const status = error.response?.status || 500
    router.push({ name: 'error', query: { status: status.toString() } })
    return Promise.reject(error)
  }

  if (originalRequest.skipGlobalErrorHandler) {
    return Promise.reject(error)
  }

  handleGlobalApiError(error, toastStore)
  return Promise.reject(error)
}
