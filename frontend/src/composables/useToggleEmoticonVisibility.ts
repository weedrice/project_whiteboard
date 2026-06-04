import type { ComputedRef } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { emoticonApi } from '@/api/emoticon'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import {
  emoticonDetailQueryKey,
  emoticonListQueryKey,
  emoticonPurchaseStatusQueryKey,
} from '@/composables/useEmoticonEditResource'

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

  return useMutation({
    mutationFn: () => emoticonApi.toggleVisibilityData(emoticonId.value),
    onSuccess: (updatedEmoticon) => {
      const isNowActive = updatedEmoticon.isActive
      toastStore.addToast(
        isNowActive ? t('emoticon.visibility.showSuccess') : t('emoticon.visibility.hiddenSuccess'),
        'success'
      )
      queryClient.invalidateQueries({ queryKey: emoticonDetailQueryKey(emoticonId) })
      if (options.invalidatePurchaseStatus) {
        queryClient.invalidateQueries({ queryKey: emoticonPurchaseStatusQueryKey(emoticonId) })
      }
      queryClient.invalidateQueries({ queryKey: emoticonListQueryKey })
    },
    onError: (err: unknown) => {
      const message = extractErrorMessage(err) || t('emoticon.edit.failed')
      toastStore.addToast(message, 'error')
    }
  })
}
