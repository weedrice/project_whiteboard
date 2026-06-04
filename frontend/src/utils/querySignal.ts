import type { QueryFunctionContext } from '@tanstack/vue-query'
import type { AxiosRequestConfig } from 'axios'

export function withQuerySignal(
  config: AxiosRequestConfig | undefined,
  queryContext?: QueryFunctionContext,
): AxiosRequestConfig {
  return {
    ...config,
    signal: config?.signal ?? queryContext?.signal,
  }
}
