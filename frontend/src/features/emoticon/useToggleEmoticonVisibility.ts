import { getCurrentScope, onScopeDispose, type ComputedRef } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useAuthScopedMutation } from '@/composables/useAuthScopedMutation'
import { useI18n } from 'vue-i18n'
import { emoticonApi } from '@/api/emoticon'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import { useAuthStore } from '@/stores/auth'
import {
  isSessionGenerationCurrent,
  sessionQueryKey,
} from '@/queryAuthScope'

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
  let disposed = false

  if (getCurrentScope()) {
    onScopeDispose(() => {
      disposed = true
    })
  }

  type VisibilityIntent = {
    sessionGeneration: number
    targetEmoticonId: number
  }

  const mutation = useAuthScopedMutation({
    mutationFn: async (intent: VisibilityIntent, mutationContext) => {
      const updatedEmoticon = await emoticonApi.toggleVisibilityData(intent.targetEmoticonId, mutationContext.withRequestConfig({
        skipGlobalErrorHandler: true,
      }))
      return { ...intent, updatedEmoticon }
    },
    onSuccess: ({ sessionGeneration, targetEmoticonId, updatedEmoticon }) => {
      if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return
      // A successful write still owns its caches after the view moves or unmounts.
      queryClient.invalidateQueries({
        queryKey: sessionQueryKey(sessionGeneration, emoticonQueryKeys.detail(targetEmoticonId)),
      })
      if (options.invalidatePurchaseStatus) {
        queryClient.invalidateQueries({
          queryKey: sessionQueryKey(sessionGeneration, emoticonQueryKeys.purchaseStatus(targetEmoticonId)),
        })
      }
      queryClient.invalidateQueries({ queryKey: emoticonQueryKeys.listRoot })
      if (disposed || targetEmoticonId !== emoticonId.value) return
      toastStore.addToast(
        updatedEmoticon.isActive ? t('emoticon.visibility.showSuccess') : t('emoticon.visibility.hiddenSuccess'),
        'success'
      )
    },
    onError: (err: unknown, intent) => {
      if (
        disposed
        || intent.targetEmoticonId !== emoticonId.value
        || !isSessionGenerationCurrent(authStore, intent.sessionGeneration)
      ) return
      const message = extractErrorMessage(err) || t('emoticon.edit.failed')
      toastStore.addToast(message, 'error')
    },
  })

  const createIntent = (): VisibilityIntent => ({
    sessionGeneration: authStore.sessionGeneration,
    targetEmoticonId: emoticonId.value,
  })

  return {
    ...mutation,
    mutate: () => mutation.mutate(createIntent()),
    mutateAsync: () => mutation.mutateAsync(createIntent()),
  }
}
