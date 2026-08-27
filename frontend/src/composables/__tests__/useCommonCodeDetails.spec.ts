import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  useStrictSupportedCommonCodeValues,
  useSupportedCommonCodeValues,
} from '../useCommonCodeDetails'
import { QUERY_STALE_TIME } from '@/utils/constants'

const mocks = vi.hoisted(() => ({
  data: undefined as Array<{
    id: number
    typeCode: string
    codeValue: string
    codeName: string
    sortOrder: number
    isActive: boolean
  }> | undefined,
  isError: false,
  isLoading: false,
  isFetching: false,
  queryOptions: null as Record<string, unknown> | null,
  refetch: vi.fn(),
}))

vi.mock('@/api/commonCode', () => ({
  commonCodeApi: { getDetails: vi.fn() },
}))

vi.mock('@/composables/useApiQuery', () => ({
  useApiQuery: (options: Record<string, unknown>) => {
    mocks.queryOptions = options
    return {
      data: {
        get value() {
          return mocks.data
        },
      },
      isError: {
        get value() {
          return mocks.isError
        },
      },
      isLoading: {
        get value() {
          return mocks.isLoading
        },
      },
      isFetching: {
        get value() {
          return mocks.isFetching
        },
      },
      refetch: mocks.refetch,
    }
  },
}))

describe('useSupportedCommonCodeValues', () => {
  beforeEach(() => {
    mocks.data = undefined
    mocks.isError = false
    mocks.isLoading = false
    mocks.isFetching = false
    mocks.queryOptions = null
    mocks.refetch.mockReset()
  })

  it('uses supported values as a loading and error fallback', () => {
    const values = useSupportedCommonCodeValues('TEST_TYPE', ['FIRST', 'SECOND'] as const)

    expect(values.value).toEqual(['FIRST', 'SECOND'])
  })

  it('keeps the DB order while excluding inactive and unsupported values', () => {
    mocks.data = [
      { id: 1, typeCode: 'TEST_TYPE', codeValue: 'SECOND', codeName: 'Second', sortOrder: 10, isActive: true },
      { id: 2, typeCode: 'TEST_TYPE', codeValue: 'LEGACY', codeName: 'Legacy', sortOrder: 20, isActive: true },
      { id: 3, typeCode: 'TEST_TYPE', codeValue: 'FIRST', codeName: 'First', sortOrder: 30, isActive: true },
      { id: 4, typeCode: 'TEST_TYPE', codeValue: 'THIRD', codeName: 'Third', sortOrder: 40, isActive: false },
    ]

    const values = useSupportedCommonCodeValues(
      'TEST_TYPE',
      ['FIRST', 'SECOND', 'THIRD'] as const,
    )

    expect(values.value).toEqual(['SECOND', 'FIRST'])
  })

  it('is not ready while command values are loading', () => {
    mocks.isLoading = true
    mocks.isFetching = true
    const strict = useStrictSupportedCommonCodeValues('TEST_TYPE', ['FIRST', 'SECOND'] as const)

    expect(strict.values.value).toEqual([])
    expect(strict.isLoading.value).toBe(true)
    expect(strict.isReady.value).toBe(false)
  })

  it('keeps cached values but is not ready after a revalidation error', () => {
    mocks.isError = true
    mocks.data = [
      { id: 1, typeCode: 'TEST_TYPE', codeValue: 'FIRST', codeName: 'First', sortOrder: 10, isActive: true },
    ]
    const strict = useStrictSupportedCommonCodeValues('TEST_TYPE', ['FIRST', 'SECOND'] as const)

    expect(strict.values.value).toEqual(['FIRST'])
    expect(strict.isError.value).toBe(true)
    expect(strict.isReady.value).toBe(false)
  })

  it('fails closed while cached command values are being revalidated', () => {
    mocks.data = [
      { id: 1, typeCode: 'TEST_TYPE', codeValue: 'FIRST', codeName: 'First', sortOrder: 10, isActive: true },
    ]
    mocks.isFetching = true

    const strict = useStrictSupportedCommonCodeValues('TEST_TYPE', ['FIRST'] as const)

    expect(strict.values.value).toEqual(['FIRST'])
    expect(strict.isLoading.value).toBe(false)
    expect(strict.isValidating.value).toBe(true)
    expect(strict.isReady.value).toBe(false)
  })

  it('is ready after supported command values load successfully', () => {
    mocks.data = [
      { id: 1, typeCode: 'TEST_TYPE', codeValue: 'FIRST', codeName: 'First', sortOrder: 10, isActive: true },
    ]

    const strict = useStrictSupportedCommonCodeValues('TEST_TYPE', ['FIRST'] as const)

    expect(strict.values.value).toEqual(['FIRST'])
    expect(strict.isReady.value).toBe(true)
  })

  it('forwards the consumer enabled state to the shared query', () => {
    useStrictSupportedCommonCodeValues('TEST_TYPE', ['FIRST'] as const, { enabled: false })

    expect(mocks.queryOptions).toMatchObject({ enabled: false })
  })

  it('refreshes strict command codes frequently and on browser lifecycle events', () => {
    useStrictSupportedCommonCodeValues('TEST_TYPE', ['FIRST'] as const)

    expect(mocks.queryOptions).toMatchObject({
      staleTime: QUERY_STALE_TIME.SHORT,
      refetchInterval: QUERY_STALE_TIME.SHORT,
      refetchOnMount: 'always',
      refetchOnWindowFocus: 'always',
      refetchOnReconnect: 'always',
      retry: false,
    })
  })
})
