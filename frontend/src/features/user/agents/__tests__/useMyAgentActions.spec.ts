import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, reactive, ref, type EffectScope } from 'vue'
import { useMyAgentActions } from '../useMyAgentActions'
import { createDeferred } from '@/test/async'

const scopes: EffectScope[] = []
function setup() {
  const scope = effectScope()
  scopes.push(scope)
  const authSession = reactive({ sessionGeneration: 1, user: { userId: 1 }, accessToken: 'first' })
  const options = {
    authSession, agentsData: ref({ agents: [] }), isAgentsLoading: ref(false), isAgentsFetching: ref(false),
    isEmailVerified: ref(true), claimAgent: vi.fn().mockResolvedValue(undefined),
    suspendMyAgent: vi.fn().mockResolvedValue(undefined), confirm: vi.fn().mockResolvedValue(true),
    addToast: vi.fn(), onRefreshed: vi.fn(), t: (key: string) => key,
  }
  return { actions: scope.run(() => useMyAgentActions(options))!, options }
}

afterEach(() => scopes.splice(0).forEach(scope => scope.stop()))

describe('useMyAgentActions session ownership', () => {
  it.each(['generation', 'principal'] as const)('does not suspend after confirmation crosses a %s boundary', async boundary => {
    const { actions, options } = setup()
    const approval = createDeferred<boolean>()
    options.confirm.mockReturnValueOnce(approval.promise)
    const pending = actions.handleSuspendAgent(7)
    if (boundary === 'generation') options.authSession.sessionGeneration += 1
    else options.authSession.user.userId = 2
    approval.resolve(true)
    await pending
    expect(options.suspendMyAgent).not.toHaveBeenCalled()
    expect(options.addToast).not.toHaveBeenCalled()
  })

  it('clears claim secrets on boundary and prevents an old response from clearing the next pending request', async () => {
    const { actions, options } = setup()
    const first = createDeferred<void>()
    const second = createDeferred<void>()
    options.claimAgent.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    actions.agentToken.value = 'old-token'
    const oldClaim = actions.handleClaimAgent()
    options.authSession.sessionGeneration += 1
    expect(actions.agentToken.value).toBe('')
    expect(actions.isClaiming.value).toBe(false)
    actions.agentToken.value = 'new-token'
    const newClaim = actions.handleClaimAgent()
    first.reject(new Error('old failure'))
    await oldClaim
    expect(actions.isClaiming.value).toBe(true)
    expect(actions.agentToken.value).toBe('new-token')
    expect(actions.agentError.value).toBe('')
    expect(options.addToast).not.toHaveBeenCalled()
    expect(options.onRefreshed).not.toHaveBeenCalled()
    second.resolve()
    await newClaim
    expect(actions.isClaiming.value).toBe(false)
    expect(options.onRefreshed).toHaveBeenCalledOnce()
  })

  it('preserves a new suspension while a previous session rejects', async () => {
    const { actions, options } = setup()
    const first = createDeferred<void>()
    const second = createDeferred<void>()
    options.suspendMyAgent.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const oldRun = actions.handleSuspendAgent(7)
    await Promise.resolve()
    options.authSession.sessionGeneration += 1
    const newRun = actions.handleSuspendAgent(8)
    await Promise.resolve()
    first.reject(new Error('old failure'))
    await oldRun
    expect(actions.processingAgentId.value).toBe(8)
    expect(actions.processingAction.value).toBe('suspend')
    expect(options.addToast).not.toHaveBeenCalled()
    second.resolve()
    await newRun
    expect(actions.processingAgentId.value).toBeNull()
    expect(options.addToast).toHaveBeenCalledWith('user.profile.agentSuspendSuccess', 'success')
  })

  it('allows same-session token rotation and preserves a newly typed token', async () => {
    const { actions, options } = setup()
    const response = createDeferred<void>()
    options.claimAgent.mockReturnValueOnce(response.promise)
    actions.agentToken.value = 'submitted'
    const pending = actions.handleClaimAgent()
    options.authSession.accessToken = 'refreshed'
    actions.agentToken.value = 'next-token'
    response.resolve()
    await pending
    expect(actions.agentToken.value).toBe('next-token')
    expect(options.addToast).toHaveBeenCalledWith('user.profile.agentClaimSuccess', 'success')
  })
})
