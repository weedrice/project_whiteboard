import axios, { type AxiosInstance } from 'axios'
import { API } from '@/utils/constants'
import {
  getCurrentPathname,
  installApiInterceptors,
  isLoginPathname,
} from '@/api/apiInterceptors'
import { configureApiStoreResolvers } from '@/api/apiStoreResolvers'

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
  getCurrentPathname,
  isLoginPathname,
}

export default api
