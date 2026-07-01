import type { AxiosRequestConfig } from 'axios'

export function withQuerySignal(
  config: AxiosRequestConfig | undefined,
  queryContext?: { signal?: AbortSignal },
): AxiosRequestConfig {
  const signal = config?.signal ?? queryContext?.signal
  if (!signal) {
    return { ...config }
  }

  return {
    ...config,
    signal,
  }
}

export function optionalQuerySignal(
  config: AxiosRequestConfig | undefined,
  queryContext?: { signal?: AbortSignal },
): AxiosRequestConfig | undefined {
  const nextConfig = withQuerySignal(config, queryContext)
  return Object.keys(nextConfig).length > 0 ? nextConfig : undefined
}
