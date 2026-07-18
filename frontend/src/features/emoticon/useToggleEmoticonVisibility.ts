import { getCurrentScope, onScopeDispose, type ComputedRef } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { emoticonApi } from '@/api/emoticon'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import { useAuthStore } from '@/stores/auth'
import {
  currentSessionQueryKey,
  isSessionGenerationCurrent,
  subscribeAuthSessionBoundary,
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
  const activeControllers = new Set<AbortController>()

  if (getCurrentScope()) {
    const stopSessionBoundaryListener = subscribeAuthSessionBoundary(() => {
      activeControllers.forEach((controller) => controller.abort())
      activeControllers.clear()
    })
    onScopeDispose(() => {
      stopSessionBoundaryListener()
      activeControllers.forEach((controller) => controller.abort())
      activeControllers.clear()
    })
  }

  type VisibilityIntent = {
    controller: AbortController
    sessionGeneration: number
    targetEmoticonId: number
  }

  const mutation = useMutation({
    mutationFn: async (intent: VisibilityIntent) => {
      activeControllers.add(intent.controller)
      const updatedEmoticon = await emoticonApi.toggleVisibilityData(intent.targetEmoticonId, {
        signal: intent.controller.signal,
        skipGlobalErrorHandler: true,
      })
      return { ...intent, updatedEmoticon }
    },
    onSuccess: ({ controller, sessionGeneration, targetEmoticonId, updatedEmoticon }) => {
      if (
        controller.signal.aborted
        || targetEmoticonId !== emoticonId.value
        || !isSessionGenerationCurrent(authStore, sessionGeneration)
      ) return
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
    onError: (err: unknown, intent) => {
      if (
        intent.controller.signal.aborted
        || intent.targetEmoticonId !== emoticonId.value
        || !isSessionGenerationCurrent(authStore, intent.sessionGeneration)
      ) return
      const message = extractErrorMessage(err) || t('emoticon.edit.failed')
      toastStore.addToast(message, 'error')
    },
    onSettled: (_data, _error, intent) => {
      activeControllers.delete(intent.controller)
    },
  })

  const createIntent = (): VisibilityIntent => ({
    controller: new AbortController(),
    sessionGeneration: authStore.sessionGeneration,
    targetEmoticonId: emoticonId.value,
  })

  return {
    ...mutation,
    mutate: () => mutation.mutate(createIntent()),
    mutateAsync: () => mutation.mutateAsync(createIntent()),
  }
}
