import type { ComputedRef } from 'vue'
import type { Router } from 'vue-router'
import type { EmoticonDetailViewModel } from '@/features/emoticon/detail/useEmoticonDetailViewModel'

interface UseEmoticonDetailActionsOptions {
  canPurchase: ComputedRef<boolean>
  confirm: (message: string) => Promise<boolean>
  emoticonId: ComputedRef<number>
  emoticonView: ComputedRef<EmoticonDetailViewModel | null>
  purchase: () => void
  purchasePrice: ComputedRef<number>
  router: Router
  t: (key: string, params?: Record<string, unknown>) => string
  toggleVisibility: () => void
}

export function useEmoticonDetailActions(options: UseEmoticonDetailActionsOptions) {
  function goToList() {
    options.router.push({ name: 'emoticon-list' })
  }

  function goToEdit() {
    options.router.push({ name: 'emoticon-edit', params: { emoticonId: options.emoticonId.value } })
  }

  async function handlePurchase() {
    if (!options.canPurchase.value) return
    const targetEmoticonId = options.emoticonId.value
    const isConfirmed = await options.confirm(options.t('emoticon.purchase.confirm', {
      price: options.purchasePrice.value,
    }))
    if (
      !isConfirmed
      || options.emoticonId.value !== targetEmoticonId
      || !options.canPurchase.value
    ) return

    options.purchase()
  }

  async function handleToggleVisibility() {
    if (!options.emoticonView.value) return
    const targetEmoticonId = options.emoticonId.value
    const message = options.emoticonView.value.isActive
      ? options.t('emoticon.visibility.hideConfirm')
      : options.t('emoticon.visibility.showConfirm')
    const isConfirmed = await options.confirm(message)
    if (
      !isConfirmed
      || options.emoticonId.value !== targetEmoticonId
      || !options.emoticonView.value
    ) return

    options.toggleVisibility()
  }

  return {
    goToEdit,
    goToList,
    handlePurchase,
    handleToggleVisibility,
  }
}
