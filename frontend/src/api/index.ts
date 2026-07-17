import axios, { type AxiosInstance } from 'axios'
import { API } from '@/utils/constants'
import {
  getCurrentPathname,
  installApiInterceptors,
  isLoginPathname,
} from '@/api/apiInterceptors'
import { configureApiStoreResolvers } from '@/api/apiStoreResolvers'
import { configureApiLocaleResolver } from '@/api/apiLocaleHeader'

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipGlobalErrorHandler?: boolean
    skipAuthRefresh?: boolean
  }
  export interface InternalAxiosRequestConfig {
    _retry?: boolean
    redirectOnError?: boolean
    skipGlobalErrorHandler?: boolean
    skipAuthRefresh?: boolean
    _authSessionGeneration?: number
    _authAccessToken?: string | null
  }
}

const api: AxiosInstance = axios.create({
  baseURL: API.BASE_URL,
  timeout: API.TIMEOUT,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

installApiInterceptors(api)

export {
  configureApiStoreResolvers,
  configureApiLocaleResolver,
  getCurrentPathname,
  isLoginPathname,
}

export { resetAuthRefreshFailureCooldownForTest } from '@/api/apiRefreshRetry'

export default api
