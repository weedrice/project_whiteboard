import { computed, ref, type Ref } from 'vue'
import { useAuthScopedOperation } from '@/composables/useAuthScopedOperation'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'
import type { AuthSessionIntentSource } from '@/utils/authSessionIntent'
import type { AxiosError } from 'axios'
import type { UserAgent } from '@/api/user'
import { extractErrorMessage } from '@/utils/errorHandler'

interface UseMyAgentActionsOptions {
  agentsData: Ref<{ agents: UserAgent[] } | undefined>
  isAgentsLoading: Ref<boolean>
  isAgentsFetching: Ref<boolean>
  authSession: AuthSessionIntentSource
  isEmailVerified: Ref<boolean>
  claimAgent: (token: string) => Promise<unknown>
  suspendMyAgent: (agentId: number) => Promise<unknown>
  confirm: (message: string, title?: string, confirmText?: string, cancelText?: string) => Promise<boolean>
  addToast: (message: string, type: 'success' | 'error' | 'warning' | 'info') => void
  t: (key: string) => string
  onRefreshed: () => void
}

export function useMyAgentActions(options: UseMyAgentActionsOptions) {
  const operations = useAuthScopedOperation(options.authSession)
  const isClaiming = computed(() => operations.isPending('claim'))
  const agentToken = ref('')
  const agentError = ref('')
  const processingAgentId = ref<number | null>(null)
  const processingAction = ref<'suspend' | null>(null)

  const agents = computed(() => options.agentsData.value?.agents ?? [])
  const activeAgent = computed(() => agents.value.find((agent) => agent.status === 'ACTIVE') ?? null)
  const isAgentListPending = computed(() => options.isAgentsLoading.value || options.isAgentsFetching.value)

  useAuthSessionReset(() => {
    agentToken.value = ''
    agentError.value = ''
    processingAgentId.value = null
    processingAction.value = null
  }, options.authSession)

  const handleClaimAgent = async () => {
    if (isClaiming.value) return
    agentError.value = ''
    if (!options.isEmailVerified.value) {
      const message = options.t('user.profile.agentEmailVerificationRequired')
      agentError.value = message
      options.addToast(message, 'warning')
      return
    }

    const token = agentToken.value.trim()
    if (!token) {
      agentError.value = options.t('user.profile.agentCodeRequired')
      return
    }

    await operations.run('claim', async (scope) => {
      try {
        await options.claimAgent(token)
        scope.assertCurrent()
        if (agentToken.value.trim() === token) agentToken.value = ''
        options.addToast(options.t('user.profile.agentClaimSuccess'), 'success')
        options.onRefreshed()
      } catch (error: unknown) {
        scope.runIfCurrent(() => {
          agentError.value = extractErrorMessage(error as AxiosError) || options.t('user.profile.agentClaimFailed')
        })
      }
    })
  }

  const handleSuspendAgent = async (agentId: number) => {
    await operations.run('suspend', async (scope) => {
      const isConfirmed = await options.confirm(
        options.t('user.profile.agentSuspendConfirmMessage'),
        options.t('user.profile.agentSuspendConfirmTitle'),
        options.t('user.profile.agentSuspend'),
        options.t('common.cancel')
      )
      scope.assertCurrent()
      if (!isConfirmed) return
      processingAgentId.value = agentId
      processingAction.value = 'suspend'
      try {
        await options.suspendMyAgent(agentId)
        scope.assertCurrent()
        options.addToast(options.t('user.profile.agentSuspendSuccess'), 'success')
        options.onRefreshed()
      } catch (error: unknown) {
        scope.runIfCurrent(() => options.addToast(
          extractErrorMessage(error as AxiosError) || options.t('user.profile.agentSuspendFailed'), 'error',
        ))
      } finally {
        scope.runIfCurrent(() => {
          processingAgentId.value = null
          processingAction.value = null
        })
      }
    })
  }

  return {
    isClaiming,
    agentToken,
    agentError,
    processingAgentId,
    processingAction,
    agents,
    activeAgent,
    isAgentListPending,
    handleClaimAgent,
    handleSuspendAgent
  }
}
