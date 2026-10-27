import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { effectScope, reactive, type EffectScope } from 'vue'
import { useDraftDiscardConfirmation } from '../useDraftDiscardConfirmation'
import { useConfirmStore } from '@/stores/confirm'
import { useAuthStore } from '@/stores/auth'

vi.mock('vue-i18n', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-i18n')>(),
  useI18n: () => ({ t: (key: string) => key }),
}))

let pinia: ReturnType<typeof createPinia>
const owners: EffectScope[] = []
beforeEach(() => { pinia = createPinia(); setActivePinia(pinia) })
afterEach(() => { owners.splice(0).forEach(owner => owner.stop()); disposePinia(pinia) })

function setup() {
  const state = reactive({ text: 'draft', partner: 1, open: true, pending: false })
  const close = vi.fn()
  const owner = effectScope()
  owners.push(owner)
  const confirmation = useConfirmStore()
  const helper = owner.run(() => useDraftDiscardConfirmation({
    draftState: () => [state.text, state.partner, state.open, state.pending],
    isDirty: () => state.text.trim().length > 0,
    canClose: () => state.open && !state.pending,
    message: () => 'Discard draft?',
    close,
  }))!
  return { ...helper, state, close, owner, confirmation, auth: useAuthStore() }
}

describe('draft discard confirmation', () => {
  it('closes a pristine draft immediately without a prompt', async () => {
    const { requestClose, state, confirmation, close } = setup()
    state.text = ''
    await requestClose()
    expect(close).toHaveBeenCalledOnce()
    expect(confirmation.isOpen).toBe(false)
  })

  it('preserves on cancel and discards only after explicit approval', async () => {
    const { requestClose, confirmation, close, state } = setup()
    const cancelled = requestClose()
    expect(confirmation.isOpen).toBe(true)
    confirmation.cancel()
    await cancelled
    expect(close).not.toHaveBeenCalled()
    expect(state.text).toBe('draft')
    const approved = requestClose()
    confirmation.confirm()
    await approved
    expect(close).toHaveBeenCalledOnce()
  })

  it('does not replace a pending prompt on repeated close attempts', async () => {
    const { requestClose, confirmation, close } = setup()
    const open = vi.spyOn(confirmation, 'open')
    const first = requestClose()
    await requestClose()
    expect(open).toHaveBeenCalledOnce()
    confirmation.confirm()
    await first
    expect(close).toHaveBeenCalledOnce()
  })

  it.each(['text', 'partner', 'open', 'pending'] as const)('cancels its prompt when %s changes', async (field) => {
    const { requestClose, confirmation, close, state } = setup()
    const pending = requestClose()
    if (field === 'text') state.text = 'new draft'
    else if (field === 'partner') state.partner = 2
    else if (field === 'open') state.open = false
    else state.pending = true
    expect(confirmation.isOpen).toBe(false)
    confirmation.confirm()
    await pending
    expect(close).not.toHaveBeenCalled()
  })

  it('rejects an already approved result if the content changes before continuation', async () => {
    const { requestClose, confirmation, close, state } = setup()
    const pending = requestClose()
    confirmation.confirm()
    state.text = 'newer draft'
    await pending
    expect(close).not.toHaveBeenCalled()
  })

  it('cancels on session change and cannot close the next session draft', async () => {
    const { requestClose, confirmation, close, auth } = setup()
    const pending = requestClose()
    auth.sessionGeneration++
    expect(confirmation.isOpen).toBe(false)
    confirmation.confirm()
    await pending
    expect(close).not.toHaveBeenCalled()
  })

  it('cancels on account replacement even without a generation notification', async () => {
    const { requestClose, confirmation, close, auth } = setup()
    const pending = requestClose()
    auth.user = {
      userId: 2, loginId: 'another', displayName: 'Another user', email: 'another@example.com',
      status: 'ACTIVE', createdAt: '2026-01-01T00:00:00',
    }
    expect(confirmation.isOpen).toBe(false)
    confirmation.confirm()
    await pending
    expect(close).not.toHaveBeenCalled()
  })

  it('preserves confirmation through ordinary token rotation', async () => {
    const { requestClose, confirmation, close, auth } = setup()
    const pending = requestClose()
    auth.accessToken = 'rotated-test-token'
    expect(confirmation.isOpen).toBe(true)
    confirmation.confirm()
    await pending
    expect(close).toHaveBeenCalledOnce()
  })

  it('removes its owned prompt when the editor unmounts', async () => {
    const { requestClose, confirmation, close, owner } = setup()
    const pending = requestClose()
    owner.stop()
    expect(confirmation.isOpen).toBe(false)
    await pending
    expect(close).not.toHaveBeenCalled()
  })

  it('does not remove a replacement prompt when its editor unmounts', async () => {
    const { requestClose, confirmation, close, owner } = setup()
    const pending = requestClose()
    const replacement = confirmation.open('Another action')
    owner.stop()
    expect(confirmation.isOpen).toBe(true)
    expect(confirmation.message).toBe('Another action')
    confirmation.confirm()
    await expect(replacement).resolves.toBe(true)
    await pending
    expect(close).not.toHaveBeenCalled()
  })

  it('leaves a sending conversation open without prompting', async () => {
    const { requestClose, confirmation, close, state } = setup()
    state.pending = true
    await requestClose()
    expect(confirmation.isOpen).toBe(false)
    expect(close).not.toHaveBeenCalled()
  })
})
