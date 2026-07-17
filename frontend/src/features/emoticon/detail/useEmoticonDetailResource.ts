import { computed, type ComputedRef } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import type { AxiosResponse } from 'axios'
import { useI18n } from 'vue-i18n'
import { emoticonApi } from '@/api/emoticon'
import { useApiQuery } from '@/composables/useApiQuery'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useToggleEmoticonVisibility } from '@/features/emoticon/useToggleEmoticonVisibility'
import { useEmoticonPermissions } from '@/features/emoticon/detail/useEmoticonPermissions'
import { useEmoticonDetailViewModel } from '@/features/emoticon/detail/useEmoticonDetailViewModel'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import { userQueryKeys } from '@/composables/userQueryKeys'
import { extractErrorMessage } from '@/utils/errorHandler'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'
import type { ApiResponse } from '@/types'
import type { EmoticonMaster, EmoticonPurchaseStatus } from '@/types/emoticon'
import {
  AUTH_SCOPED_QUERY_META,
  currentSessionQueryKey,
  isSessionGenerationCurrent,
} from '@/queryAuthScope'

export function useEmoticonDetailResource(emoticonId: ComputedRef<number>) {
  const { t } = useI18n()
  const authStore = useAuthStore()
  const queryClient = useQueryClient()
  const toastStore = useToastStore()

  const { data: emoticon, isLoading, error } = useApiQuery({
    queryKey: computed(() => emoticonQueryKeys.detail(emoticonId.value)),
    request: (context) => callWithOptionalQuerySignal(
      context,
      () => emoticonApi.getEmoticon(emoticonId.value),
      (config) => emoticonApi.getEmoticon(emoticonId.value, config),
    ) as Promise<AxiosResponse<ApiResponse<EmoticonMaster>>>,
    enabled: computed(() => !!emoticonId.value),
  })

  const {
    data: purchaseStatus,
    isLoading: isPurchaseStatusLoading,
    isFetching: isPurchaseStatusFetching,
    error: purchaseStatusError,
  } = useApiQuery({
    queryKey: computed(() => emoticonQueryKeys.purchaseStatus(emoticonId.value)),
    request: (context) => callWithOptionalQuerySignal(
      context,
      () => emoticonApi.checkPurchaseStatus(emoticonId.value),
      (config) => emoticonApi.checkPurchaseStatus(emoticonId.value, config),
    ) as Promise<AxiosResponse<ApiResponse<EmoticonPurchaseStatus>>>,
    enabled: computed(() => !!emoticonId.value),
    meta: AUTH_SCOPED_QUERY_META,
  })

  const emoticonView = useEmoticonDetailViewModel(emoticon)

  const { mutate: purchaseEmoticon, isPending: isPurchasing } = useMutation({
    onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
    mutationFn: async (targetEmoticonId: number) => {
      await emoticonApi.purchaseEmoticon(targetEmoticonId)
      return { targetEmoticonId }
    },
    onSuccess: ({ targetEmoticonId }, _variables, context) => {
      if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
      toastStore.addToast(t('emoticon.purchase.success'), 'success')
      queryClient.invalidateQueries({ queryKey: emoticonQueryKeys.detail(targetEmoticonId) })
      queryClient.invalidateQueries({
        queryKey: currentSessionQueryKey(authStore, emoticonQueryKeys.purchaseStatus(targetEmoticonId)),
      })
      queryClient.invalidateQueries({
        queryKey: currentSessionQueryKey(authStore, emoticonQueryKeys.accessiblePicker),
      })
      queryClient.invalidateQueries({ queryKey: emoticonQueryKeys.listRoot })
      queryClient.invalidateQueries({
        queryKey: currentSessionQueryKey(authStore, userQueryKeys.pointsRoot),
      })
    },
    onError: (error: unknown, _variables, context) => {
      if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
      const message = extractErrorMessage(error) || t('emoticon.purchase.failed')
      toastStore.addToast(message, 'error')
    },
  })
  const purchase = () => purchaseEmoticon(emoticonId.value)

  const { isOwner } = useEmoticonPermissions({
    isAuthenticated: () => authStore.isAuthenticated,
    getCreatorId: () => emoticonView.value?.creatorId,
    getUserId: () => authStore.user?.userId,
  })

  const isPurchaseStatusPending = computed(() => (
    !!emoticonId.value
    && (isPurchaseStatusLoading.value || isPurchaseStatusFetching.value)
  ))

  const canPurchase = computed(() => {
    if (!authStore.isAuthenticated) return false
    if (!emoticonView.value) return false
    if (isPurchaseStatusPending.value) return false
    if (purchaseStatusError.value) return false
    if (!emoticonView.value.isActive) return false
    if (purchaseStatus.value?.available !== true) return false
    if (purchaseStatus.value?.purchased) return false
    if (isOwner.value) return false
    return true
  })

  const purchasePrice = computed(() => purchaseStatus.value?.price ?? 100)

  const purchaseButtonText = computed(() => {
    if (purchaseStatusError.value || purchaseStatus.value?.available !== true) {
      return t('emoticon.purchase.button.unavailable')
    }
    if (!authStore.isAuthenticated) return t('emoticon.purchase.button.loginRequired')
    if (purchaseStatus.value?.purchased) return t('emoticon.purchase.button.purchased')
    if (isOwner.value) return t('emoticon.purchase.button.myEmoticon')
    return t('emoticon.purchase.button.buyWithPrice', { price: purchasePrice.value })
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
    purchasePrice,
    toggleVisibility,
  }
}
