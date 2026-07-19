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
      authSession: { sessionGeneration: 0, user: { userId: 1 } },
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

  it('does not purchase with the next account after confirmation resolves', async () => {
    const authSession = { sessionGeneration: 3, user: { userId: 1 } }
    let resolveConfirmation!: (confirmed: boolean) => void
    const confirm = vi.fn(() => new Promise<boolean>((resolve) => {
      resolveConfirmation = resolve
    }))
    const purchase = vi.fn()
    const actions = useEmoticonDetailActions({
      authSession,
      canPurchase: computed(() => true),
      confirm,
      emoticonId: computed(() => 7),
      emoticonView: computed(() => ({ isActive: true }) as never),
      purchase,
      purchasePrice: computed(() => 100),
      router: { push: vi.fn() } as never,
      t: (key) => key,
      toggleVisibility: vi.fn(),
    })

    const purchasing = actions.handlePurchase()
    authSession.sessionGeneration = 4
    authSession.user.userId = 2
    resolveConfirmation(true)
    await purchasing

    expect(purchase).not.toHaveBeenCalled()
  })
})
