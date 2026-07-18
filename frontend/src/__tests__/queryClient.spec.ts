import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosError } from 'axios'

const mocks = vi.hoisted(() => ({
  loggerError: vi.fn(),
  extractErrorMessage: vi.fn(() => 'translated error'),
  suppress: vi.fn(() => false),
  createErrorLogPayload: vi.fn(() => ({ safe: true })),
}))

vi.mock('@/utils/logger', () => ({ default: { error: mocks.loggerError } }))
vi.mock('@/utils/errorHandler', () => ({
  extractErrorMessage: mocks.extractErrorMessage,
  shouldSuppressGlobalErrorToast: mocks.suppress,
}))
vi.mock('@/utils/vueErrorLog', () => ({ createErrorLogPayload: mocks.createErrorLogPayload }))

import { configureQueryClientStoreResolvers, queryClient } from '@/queryClient'
import { configureAuthQueryScope } from '@/queryAuthScope'

describe('queryClient defaults', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.suppress.mockReturnValue(false)
    configureAuthQueryScope(() => 1)
  })

  it('suppresses errors from a mutation completed after its session generation became stale', () => {
    const addToast = vi.fn()
    configureQueryClientStoreResolvers({ resolveToastStore: () => ({ addToast }) })
    const mutationConfig = queryClient.getMutationCache().config

    mutationConfig.onError?.(
      new Error('old account'),
      undefined,
      { sessionGeneration: 0 },
      { meta: { authScoped: true } } as never,
      {} as never,
    )

    expect(addToast).not.toHaveBeenCalled()
    expect(mocks.loggerError).not.toHaveBeenCalled()
  })

  it('keeps cache defaults and retry boundaries stable', () => {
    const queries = queryClient.getDefaultOptions().queries!
    expect(queries.staleTime).toBe(30_000)
    expect(queries.placeholderData).toBeUndefined()
    expect(queries.refetchOnWindowFocus).toBe(false)
    expect(queries.refetchOnReconnect).toBe(true)
    const retryDelay = queries.retryDelay as (attempt: number, error: Error) => number
    expect(retryDelay(0, new Error())).toBe(1_000)
    expect(retryDelay(10, new Error())).toBe(30_000)

    const retry = queries.retry as (count: number, error: unknown) => boolean
    expect(retry(0, {})).toBe(true)
    expect(retry(2, {})).toBe(false)
    expect(retry(1, { response: { status: 503 } } as AxiosError)).toBe(true)
    expect(retry(2, { response: { status: 503 } } as AxiosError)).toBe(false)
    expect(retry(2, { response: { status: 429 } } as AxiosError)).toBe(true)
    expect(retry(3, { response: { status: 429 } } as AxiosError)).toBe(false)
    expect(retry(0, { response: { status: 400 } } as AxiosError)).toBe(false)
  })

  it('routes query and mutation errors to the configured toast store and safe logger', () => {
    const addToast = vi.fn()
    configureQueryClientStoreResolvers({ resolveToastStore: () => ({ addToast }) })
    const error = new Error('secret')
    const queryConfig = queryClient.getQueryCache().config
    const mutationConfig = queryClient.getMutationCache().config

    queryConfig.onError?.(error, { meta: {} } as never)
    mutationConfig.onError?.(error, undefined, undefined, { meta: {} } as never, {} as never)

    expect(addToast).toHaveBeenCalledTimes(2)
    expect(addToast).toHaveBeenCalledWith('translated error', 'error')
    expect(mocks.loggerError).toHaveBeenCalledTimes(2)
    expect(mocks.createErrorLogPayload).toHaveBeenCalledWith(error)
  })

  it('suppresses opted-out, auth, and unconfigured-store toasts', () => {
    configureQueryClientStoreResolvers({ resolveToastStore: () => null as never })
    const error = new Error('ignored')
    const queryConfig = queryClient.getQueryCache().config
    const mutationConfig = queryClient.getMutationCache().config

    queryConfig.onError?.(error, { meta: { errorMessage: false } } as never)
    mutationConfig.onError?.(error, undefined, undefined, { meta: { errorMessage: false } } as never, {} as never)
    mocks.suppress.mockReturnValue(true)
    queryConfig.onError?.(error, { meta: {} } as never)
    mutationConfig.onError?.(error, undefined, undefined, { meta: {} } as never, {} as never)
    mocks.suppress.mockReturnValue(false)
    queryConfig.onError?.(error, { meta: {} } as never)

    expect(mocks.loggerError).toHaveBeenCalledTimes(1)
  })
})
