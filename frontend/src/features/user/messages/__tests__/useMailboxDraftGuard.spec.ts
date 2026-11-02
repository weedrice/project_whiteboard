import { mount } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { defineComponent, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { useConfirmStore } from '@/stores/confirm'
import { useMailboxDraftGuard } from '../useMailboxDraftGuard'

vi.mock('vue-i18n', async (importOriginal) => ({
    ...await importOriginal<typeof import('vue-i18n')>(),
    useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('vue-router', async (importOriginal) => ({
    ...await importOriginal<typeof import('vue-router')>(),
    onBeforeRouteLeave: vi.fn(),
}))

let pinia: ReturnType<typeof createPinia>
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
})
afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    disposePinia(pinia)
})

function setup() {
    const content = ref('Private reply')
    const sending = ref(false)
    const partner = ref<number | undefined>(2)
    let guard!: ReturnType<typeof useMailboxDraftGuard>
    const wrapper = mount(defineComponent({
        setup() {
            guard = useMailboxDraftGuard(content, sending, partner)
            return () => null
        },
    }))
    wrappers.push(wrapper)
    return { ...guard, content, sending, partner, wrapper, confirmation: useConfirmStore(), auth: useAuthStore() }
}

describe('mailbox draft navigation ownership', () => {
    it.each(['content', 'partner', 'sending'] as const)('cancels a stale decision after %s changes', async (field) => {
        const state = setup()
        const decision = state.confirmNavigation()
        expect(state.confirmation.isOpen).toBe(true)
        if (field === 'content') state.content.value = 'Newer reply'
        if (field === 'partner') state.partner.value = 3
        if (field === 'sending') state.sending.value = true
        expect(state.confirmation.isOpen).toBe(false)
        state.confirmation.confirm()
        expect(await decision).toBe(false)
        expect(state.content.value).toBe(field === 'content' ? 'Newer reply' : 'Private reply')
    })

    it('rejects a confirmation approved just before a newer edit', async () => {
        const state = setup()
        const decision = state.confirmNavigation()
        state.confirmation.confirm()
        state.content.value = 'Newer reply'
        expect(await decision).toBe(false)
        expect(state.content.value).toBe('Newer reply')
    })

    it('cancels an old session decision without clearing the next session draft', async () => {
        const state = setup()
        const decision = state.confirmNavigation()
        state.auth.sessionGeneration++
        state.content.value = 'Next session reply'
        expect(state.confirmation.isOpen).toBe(false)
        expect(await decision).toBe(false)
        expect(state.content.value).toBe('Next session reply')
    })

    it('allows ordinary token rotation while confirming discard', async () => {
        const state = setup()
        const decision = state.confirmNavigation()
        state.auth.accessToken = 'rotated-test-token'
        expect(state.confirmation.isOpen).toBe(true)
        state.confirmation.confirm()
        expect(await decision).toBe(true)
        expect(state.content.value).toBe('')
    })

    it('cancels the owned confirmation when the mailbox unmounts', async () => {
        const state = setup()
        const decision = state.confirmNavigation()
        state.wrapper.unmount()
        expect(state.confirmation.isOpen).toBe(false)
        expect(await decision).toBe(false)
        expect(state.content.value).toBe('Private reply')
    })
})
