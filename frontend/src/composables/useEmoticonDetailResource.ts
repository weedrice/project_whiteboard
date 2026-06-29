import { computed, type ComputedRef } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import type { AxiosResponse } from 'axios'
import { useI18n } from 'vue-i18n'
import { emoticonApi } from '@/api/emoticon'
import { useApiQuery } from '@/composables/useApiQuery'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useToggleEmoticonVisibility } from '@/composables/useToggleEmoticonVisibility'
import { useEmoticonPermissions } from '@/composables/useEmoticonPermissions'
import { useEmoticonDetailViewModel } from '@/composables/useEmoticonDetailViewModel'
import {
  emoticonDetailQueryKey,
  emoticonPurchaseStatusQueryKey,
} from '@/composables/useEmoticonEditResource'
import { userQueryKeys } from '@/composables/userQueryKeys'
import { extractErrorMessage } from '@/utils/errorHandler'
import type { ApiResponse } from '@/types'
import type { EmoticonMaster, EmoticonPurchaseStatus } from '@/types/emoticon'

export function useEmoticonDetailResource(emoticonId: ComputedRef<number>) {
  const { t } = useI18n()
  const authStore = useAuthStore()
  const queryClient = useQueryClient()
  const toastStore = useToastStore()

  const { data: emoticon, isLoading, error } = useApiQuery({
    queryKey: emoticonDetailQueryKey(emoticonId),
    request: () => emoticonApi.getEmoticon(emoticonId.value) as Promise<AxiosResponse<ApiResponse<EmoticonMaster>>>,
    enabled: computed(() => !!emoticonId.value),
  })

  const {
    data: purchaseStatus,
    isLoading: isPurchaseStatusLoading,
    isFetching: isPurchaseStatusFetching,
  } = useApiQuery({
    queryKey: emoticonPurchaseStatusQueryKey(emoticonId),
    request: () => emoticonApi.checkPurchaseStatus(emoticonId.value) as Promise<AxiosResponse<ApiResponse<EmoticonPurchaseStatus>>>,
    enabled: computed(() => !!emoticonId.value && authStore.isAuthenticated),
  })

  const emoticonView = useEmoticonDetailViewModel(emoticon)

  const { mutate: purchase, isPending: isPurchasing } = useMutation({
    mutationFn: () => emoticonApi.purchaseEmoticon(emoticonId.value),
    onSuccess: () => {
      toastStore.addToast(t('emoticon.purchase.success'), 'success')
      queryClient.invalidateQueries({ queryKey: emoticonDetailQueryKey(emoticonId) })
      queryClient.invalidateQueries({ queryKey: emoticonPurchaseStatusQueryKey(emoticonId) })
      queryClient.invalidateQueries({ queryKey: userQueryKeys.pointsRoot })
    },
    onError: (error: unknown) => {
      const message = extractErrorMessage(error) || t('emoticon.purchase.failed')
      toastStore.addToast(message, 'error')
    },
  })

  const { isOwner } = useEmoticonPermissions({
    isAuthenticated: () => authStore.isAuthenticated,
    getCreatorId: () => emoticonView.value?.creatorId,
    getUserId: () => authStore.user?.userId,
  })

  const isPurchaseStatusPending = computed(() => (
    authStore.isAuthenticated
    && !!emoticonId.value
    && (isPurchaseStatusLoading.value || isPurchaseStatusFetching.value)
  ))

  const canPurchase = computed(() => {
    if (!authStore.isAuthenticated) return false
    if (!emoticonView.value) return false
    if (isPurchaseStatusPending.value) return false
    if (!emoticonView.value.isActive) return false
    if (purchaseStatus.value?.purchased) return false
    if (isOwner.value) return false
    return true
  })

  const purchaseButtonText = computed(() => {
    if (!authStore.isAuthenticated) return t('emoticon.purchase.button.loginRequired')
    if (purchaseStatus.value?.purchased) return t('emoticon.purchase.button.purchased')
    if (isOwner.value) return t('emoticon.purchase.button.myEmoticon')
    return t('emoticon.purchase.button.buyWithPrice', { price: purchaseStatus.value?.price || 100 })
  })

  const { mutate: toggleVisibility, isPending: isToggling } = useToggleEmoticonVisibility(emoticonId, {
    invalidatePurchaseStatus: true,
  })

  return {
    canPurchase,
    emoticonView,
    error,
    isLoading,
    isOwner,
    isPurchaseStatusPending,
    isPurchasing,
    isToggling,
    purchase,
    purchaseButtonText,
    toggleVisibility,
  }
}
