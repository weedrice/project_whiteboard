import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAccessibleEmoticonPicker } from './useAccessibleEmoticonPicker'
import { useAuthStore } from '@/stores/auth'

const mocks = vi.hoisted(() => ({
  options: null as Record<string, unknown> | null,
}))

vi.mock('@tanstack/vue-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/vue-query')>()
  return {
    ...actual,
    useQuery: (options: Record<string, unknown>) => {
      mocks.options = options
      return options
    },
  }
})

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    getPurchasedEmoticonsData: vi.fn(),
    getMyEmoticonsData: vi.fn(),
  },
}))

describe('useAccessibleEmoticonPicker', () => {
  beforeEach(() => {
    mocks.options = null
    setActivePinia(createPinia())
    useAuthStore().sessionGeneration = 3
  })

  it('reactively moves private picker data to the current session generation', () => {
    useAccessibleEmoticonPicker(() => true)
    const queryKey = mocks.options?.queryKey as { value: readonly unknown[] }

    expect(queryKey.value).toEqual(['session', 3, 'emoticons', 'accessible', 'picker'])

    useAuthStore().sessionGeneration = 4

    expect(queryKey.value).toEqual(['session', 4, 'emoticons', 'accessible', 'picker'])
  })
})
