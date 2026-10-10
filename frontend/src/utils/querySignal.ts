import type { AxiosRequestConfig } from 'axios'

interface QueryRequestContext {
  signal?: AbortSignal
  requestConfig?: AxiosRequestConfig
  withRequestConfig?: (config?: AxiosRequestConfig) => AxiosRequestConfig
}

export function withQuerySignal(
  config: AxiosRequestConfig | undefined,
  queryContext?: QueryRequestContext,
): AxiosRequestConfig {
  if (queryContext?.withRequestConfig) return queryContext.withRequestConfig(config)
  const scopedConfig = queryContext?.requestConfig
  const querySignal = queryContext?.signal
  const signal = config?.signal instanceof AbortSignal && querySignal
    ? AbortSignal.any([config.signal, querySignal])
    : config?.signal ?? querySignal
  if (!signal) {
    return { ...config }
  }

  return {
    ...scopedConfig,
    ...config,
    ...(scopedConfig?.expectedSession ? { expectedSession: scopedConfig.expectedSession } : {}),
    signal,
  }
}

export function optionalQuerySignal(
  config: AxiosRequestConfig | undefined,
  queryContext?: QueryRequestContext,
): AxiosRequestConfig | undefined {
  const nextConfig = withQuerySignal(config, queryContext)
  return Object.keys(nextConfig).length > 0 ? nextConfig : undefined
}

export function callWithOptionalQuerySignal<T>(
  queryContext: QueryRequestContext | undefined,
  request: () => T,
  requestWithConfig: (config: AxiosRequestConfig) => T,
): T {
  const config = optionalQuerySignal(undefined, queryContext)
  return config ? requestWithConfig(config) : request()
}
