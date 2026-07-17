import { computed, defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useToggleEmoticonVisibility } from '../useToggleEmoticonVisibility'
import { emoticonApi } from '@/api/emoticon'
import { extractErrorMessage } from '@/utils/errorHandler'
import type { EmoticonMaster } from '@/types/emoticon'

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
const authState = vi.hoisted(() => ({ sessionGeneration: 0 }))

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

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/utils/errorHandler', () => ({
  extractErrorMessage: vi.fn(),
}))

const createToggleResult = (overrides: Partial<EmoticonMaster> = {}): EmoticonMaster => ({
  emoticonId: 1,
  name: 'Test Emoticon',
  tags: [],
  isActive: false,
  images: [],
  createdAt: '2026-01-01T00:00:00',
  modifiedAt: '2026-01-01T00:00:00',
  ...overrides,
})

describe('useToggleEmoticonVisibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.mutationOptions.length = 0
    authState.sessionGeneration = 0
  })

  it('toggles visibility and invalidates detail, purchased status, and list queries when requested', async () => {
    const toggleResult = createToggleResult({ isActive: false })
    vi.mocked(emoticonApi.toggleVisibilityData).mockResolvedValueOnce(toggleResult)
    const emoticonId = computed(() => 7)

    useToggleEmoticonVisibility(emoticonId, { invalidatePurchaseStatus: true })
    const options = mocks.mutationOptions.at(-1)!

    const intent = {
      controller: new AbortController(),
      sessionGeneration: 0,
      targetEmoticonId: 7,
    }
    const mutationResult = await (options.mutationFn as (_intent: typeof intent) => Promise<unknown>)(intent)
    expect(mutationResult).toEqual({ ...intent, updatedEmoticon: toggleResult })
    ;(options.onSuccess as (value: unknown) => void)(mutationResult)

    expect(emoticonApi.toggleVisibilityData).toHaveBeenCalledWith(7, { signal: intent.controller.signal })
    expect(mocks.addToast).toHaveBeenCalledWith('emoticon.visibility.hiddenSuccess', 'success')
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(1, { queryKey: ['emoticon', 7] })
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(2, {
      queryKey: ['session', 0, 'emoticon', 7, 'purchased'],
    })
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(3, { queryKey: ['emoticons'] })
  })

  it('keeps edit-page invalidation scope when purchased status invalidation is not requested', () => {
    const emoticonId = computed(() => 9)

    useToggleEmoticonVisibility(emoticonId)
    const options = mocks.mutationOptions.at(-1)!

    ;(options.onSuccess as (value: unknown) => void)({
      controller: new AbortController(),
      sessionGeneration: 0,
      targetEmoticonId: 9,
      updatedEmoticon: { isActive: true },
    })

    expect(mocks.addToast).toHaveBeenCalledWith('emoticon.visibility.showSuccess', 'success')
    expect(mocks.invalidateQueries).toHaveBeenCalledTimes(2)
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(1, { queryKey: ['emoticon', 9] })
    expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(2, { queryKey: ['emoticons'] })
  })

  it('invalidates the request target when the current route changes before success', async () => {
    const toggleResult = createToggleResult({ isActive: true })
    vi.mocked(emoticonApi.toggleVisibilityData).mockResolvedValueOnce(toggleResult)
    const currentId = ref(7)
    const emoticonId = computed(() => currentId.value)

    useToggleEmoticonVisibility(emoticonId)
    const options = mocks.mutationOptions.at(-1)!
    const intent = {
      controller: new AbortController(),
      sessionGeneration: 0,
      targetEmoticonId: 7,
    }
    const mutationResult = await (options.mutationFn as (_intent: typeof intent) => Promise<unknown>)(intent)
    currentId.value = 8
    ;(options.onSuccess as (value: unknown) => void)(mutationResult)

    expect(mocks.invalidateQueries).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('shows extracted error messages before fallback text', () => {
    vi.mocked(extractErrorMessage).mockReturnValueOnce('server says no')
    const emoticonId = computed(() => 11)
    const error = new Error('failed')

    useToggleEmoticonVisibility(emoticonId)
    const options = mocks.mutationOptions.at(-1)!

    ;(options.onError as (error: unknown, intent: unknown) => void)(error, {
      controller: new AbortController(),
      sessionGeneration: 0,
      targetEmoticonId: 11,
    })

    expect(extractErrorMessage).toHaveBeenCalledWith(error)
    expect(mocks.addToast).toHaveBeenCalledWith('server says no', 'error')
  })

  it('drops success and error callbacks after the session generation changes', () => {
    const emoticonId = computed(() => 12)
    useToggleEmoticonVisibility(emoticonId)
    const options = mocks.mutationOptions.at(-1)!
    const intent = {
      controller: new AbortController(),
      sessionGeneration: 0,
      targetEmoticonId: 12,
    }
    authState.sessionGeneration = 1

    ;(options.onSuccess as (value: unknown) => void)({
      ...intent,
      updatedEmoticon: { isActive: true },
    })
    ;(options.onError as (error: unknown, intent: unknown) => void)(new Error('late'), intent)

    expect(mocks.invalidateQueries).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })

  it('aborts an in-flight visibility request when its component unmounts', async () => {
    let resolveRequest!: (value: EmoticonMaster) => void
    vi.mocked(emoticonApi.toggleVisibilityData).mockReturnValueOnce(new Promise((resolve) => {
      resolveRequest = resolve
    }))
    const wrapper = mount(defineComponent({
      setup() {
        useToggleEmoticonVisibility(computed(() => 15))
        return () => null
      },
    }))
    const options = mocks.mutationOptions.at(-1)!
    const intent = {
      controller: new AbortController(),
      sessionGeneration: 0,
      targetEmoticonId: 15,
    }
    const request = (options.mutationFn as (_intent: typeof intent) => Promise<unknown>)(intent)

    wrapper.unmount()

    expect(intent.controller.signal.aborted).toBe(true)
    resolveRequest(createToggleResult({ emoticonId: 15 }))
    await request
  })
})
