import { computed, ref, type Ref } from 'vue'
import type { AxiosError } from 'axios'
import type { UserAgent } from '@/api/user'
import { extractErrorMessage } from '@/utils/errorHandler'

interface UseMyAgentActionsOptions {
  agentsData: Ref<{ agents: UserAgent[] } | undefined>
  isAgentsLoading: Ref<boolean>
  isAgentsFetching: Ref<boolean>
  isClaiming: Ref<boolean>
  isEmailVerified: Ref<boolean>
  claimAgent: (token: string) => Promise<unknown>
  suspendMyAgent: (agentId: number) => Promise<unknown>
  confirm: (message: string, title?: string, confirmText?: string, cancelText?: string) => Promise<boolean>
  addToast: (message: string, type: 'success' | 'error' | 'warning' | 'info') => void
  t: (key: string) => string
  onRefreshed: () => void
}

export function useMyAgentActions(options: UseMyAgentActionsOptions) {
  const agentToken = ref('')
  const agentError = ref('')
  const processingAgentId = ref<number | null>(null)
  const processingAction = ref<'suspend' | null>(null)

  const agents = computed(() => options.agentsData.value?.agents ?? [])
  const activeAgent = computed(() => agents.value.find((agent) => agent.status === 'ACTIVE') ?? null)
  const isAgentListPending = computed(() => options.isAgentsLoading.value || options.isAgentsFetching.value)

  const handleClaimAgent = async () => {
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

    try {
      await options.claimAgent(token)
      agentToken.value = ''
      options.addToast(options.t('user.profile.agentClaimSuccess'), 'success')
      options.onRefreshed()
    } catch (error: unknown) {
      agentError.value = extractErrorMessage(error as AxiosError) || options.t('user.profile.agentClaimFailed')
    }
  }

  const handleSuspendAgent = async (agentId: number) => {
    const isConfirmed = await options.confirm(
      '에이전트 코드를 비활성화하시겠습니까?',
      '에이전트 코드 비활성화',
      '비활성화',
      options.t('common.cancel')
    )

    if (!isConfirmed) {
      return
    }

    processingAgentId.value = agentId
    processingAction.value = 'suspend'
    try {
      await options.suspendMyAgent(agentId)
      options.addToast('에이전트를 비활성화했습니다.', 'success')
      options.onRefreshed()
    } catch (error: unknown) {
      options.addToast(extractErrorMessage(error as AxiosError) || '에이전트 비활성화에 실패했습니다.', 'error')
    } finally {
      processingAgentId.value = null
      processingAction.value = null
    }
  }

  return {
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
