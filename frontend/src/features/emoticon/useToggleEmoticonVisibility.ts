import type { ComputedRef } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { emoticonApi } from '@/api/emoticon'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import { useAuthStore } from '@/stores/auth'
import { currentSessionQueryKey } from '@/queryAuthScope'

interface UseToggleEmoticonVisibilityOptions {
  invalidatePurchaseStatus?: boolean
}

export function useToggleEmoticonVisibility(
  emoticonId: ComputedRef<number>,
  options: UseToggleEmoticonVisibilityOptions = {}
) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const queryClient = useQueryClient()
  const authStore = useAuthStore()

  return useMutation({
    mutationFn: async () => {
      const targetEmoticonId = emoticonId.value
      const updatedEmoticon = await emoticonApi.toggleVisibilityData(targetEmoticonId)
      return { targetEmoticonId, updatedEmoticon }
    },
    onSuccess: ({ targetEmoticonId, updatedEmoticon }) => {
      const isNowActive = updatedEmoticon.isActive
      toastStore.addToast(
        isNowActive ? t('emoticon.visibility.showSuccess') : t('emoticon.visibility.hiddenSuccess'),
        'success'
      )
      queryClient.invalidateQueries({ queryKey: emoticonQueryKeys.detail(targetEmoticonId) })
      if (options.invalidatePurchaseStatus) {
        queryClient.invalidateQueries({
          queryKey: currentSessionQueryKey(authStore, emoticonQueryKeys.purchaseStatus(targetEmoticonId)),
        })
      }
      queryClient.invalidateQueries({ queryKey: emoticonQueryKeys.listRoot })
    },
    onError: (err: unknown) => {
      const message = extractErrorMessage(err) || t('emoticon.edit.failed')
      toastStore.addToast(message, 'error')
    }
  })
}
