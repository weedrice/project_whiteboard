import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useEmoticonDetailActions } from '@/features/emoticon/detail/useEmoticonDetailActions'

describe('useEmoticonDetailActions destructive intent', () => {
  it('does not toggle the next emoticon after confirmation resolves', async () => {
    const emoticonId = ref(7)
    let resolveConfirmation!: (confirmed: boolean) => void
    const confirm = vi.fn(() => new Promise<boolean>((resolve) => {
      resolveConfirmation = resolve
    }))
    const toggleVisibility = vi.fn()
    const actions = useEmoticonDetailActions({
      canPurchase: computed(() => false),
      confirm,
      emoticonId: computed(() => emoticonId.value),
      emoticonView: computed(() => ({ isActive: true }) as never),
      purchase: vi.fn(),
      purchasePrice: computed(() => 100),
      router: { push: vi.fn() } as never,
      t: (key) => key,
      toggleVisibility,
    })

    const toggling = actions.handleToggleVisibility()
    emoticonId.value = 8
    resolveConfirmation(true)
    await toggling

    expect(toggleVisibility).not.toHaveBeenCalled()
  })
})
