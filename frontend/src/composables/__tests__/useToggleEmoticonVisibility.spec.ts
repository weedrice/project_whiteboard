import { computed } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useToggleEmoticonVisibility } from '../useToggleEmoticonVisibility'
import { emoticonApi } from '@/api/emoticon'
import { extractErrorMessage } from '@/utils/errorHandler'

const mocks = vi.hoisted(() => {
  const mutationOptions: Array<Record<string, unknown>> = []
  const invalidateQueries = vi.fn()
  const addToast = vi.fn()

  return {
    mutationOptions,
    invalidateQueries,
    addToast,
  }
})

vi.mock('@tanstack/vue-query', () => ({
  useMutation: vi.fn((options: Record<string, unknown>) => {
    mocks.mutationOptions.push(options)
    return {
      mutate: vi.fn(),
      isPending: false,
    }
  }),
  useQueryClient: () => ({
    invalidateQueries: mocks.invalidateQueries,
  }),
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    toggleVisibilityData: vi.fn(),
  },
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/utils/errorHandler', () => ({
  extractErrorMessage: vi.fn(),
}))

describe('useToggleEmoticonVisibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.mutationOptions.length = 0
  })

  it('toggles visibility and invalidates detail, purchased status, and list queries when requested', async () => {
    vi.mocked(emoticonApi.toggleVisibilityData).mockResolvedValueOnce({ isActive: false } as never)
    const emoticonId = computed(() => 7)

    useToggleEmoticonVisibility(emoticonId, { invalidatePurchaseStatus: true })
    const options = mocks.mutationOptions.at(-1)!

    await expect((options.mutationFn as () => Promise<unknown>)()).resolves.toEqual({ isActive: false })
    ;(options.onSuccess as (value: { isActive: boolean }) => void)({ isActive: false })

    expect(emoticonApi.toggleVisibilityData).toHaveBeenCalledWith(7)
    expect(mocks.addToast).toHaveBeenCalledWith('emoticon.visibility.hiddenSuccess', 'success')
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(1, { queryKey: ['emoticon', emoticonId] })
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(2, { queryKey: ['emoticon', emoticonId, 'purchased'] })
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(3, { queryKey: ['emoticons'] })
  })

  it('keeps edit-page invalidation scope when purchased status invalidation is not requested', () => {
    const emoticonId = computed(() => 9)

    useToggleEmoticonVisibility(emoticonId)
    const options = mocks.mutationOptions.at(-1)!

    ;(options.onSuccess as (value: { isActive: boolean }) => void)({ isActive: true })

    expect(mocks.addToast).toHaveBeenCalledWith('emoticon.visibility.showSuccess', 'success')
    expect(mocks.invalidateQueries).toHaveBeenCalledTimes(2)
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(1, { queryKey: ['emoticon', emoticonId] })
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(2, { queryKey: ['emoticons'] })
  })

  it('shows extracted error messages before fallback text', () => {
    vi.mocked(extractErrorMessage).mockReturnValueOnce('server says no')
    const emoticonId = computed(() => 11)
    const error = new Error('failed')

    useToggleEmoticonVisibility(emoticonId)
    const options = mocks.mutationOptions.at(-1)!

    ;(options.onError as (error: unknown) => void)(error)

    expect(extractErrorMessage).toHaveBeenCalledWith(error)
    expect(mocks.addToast).toHaveBeenCalledWith('server says no', 'error')
  })
})
