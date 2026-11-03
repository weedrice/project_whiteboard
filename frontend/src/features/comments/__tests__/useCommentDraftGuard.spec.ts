import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { computed, defineComponent, h, ref } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { useConfirmStore } from '@/stores/confirm'
import { useCommentDraftGuard } from '../useCommentDraftGuard'

vi.mock('vue-i18n', async (importOriginal) => ({
    ...await importOriginal<typeof import('vue-i18n')>(),
    useI18n: () => ({ t: (key: string) => key }),
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

async function setup(formCount = 1) {
    const forms = Array.from({ length: formCount }, () => ({
        content: ref('Private draft'),
        initial: ref(''),
        sending: ref(false),
        identity: ref('post:1'),
        confirmDiscard: undefined as ReturnType<typeof useCommentDraftGuard>['confirmDiscard'] | undefined,
    }))
    const Form = defineComponent({
        props: { index: { type: Number, required: true } },
        setup(props) {
            const state = forms[props.index]
            state.confirmDiscard = useCommentDraftGuard(
                state.content,
                computed(() => state.content.value.trim() !== state.initial.value.trim()),
                state.sending,
                state.identity,
            ).confirmDiscard
            return () => h('textarea', { value: state.content.value })
        },
    })
    const Page = defineComponent({ setup: () => () => h('div', forms.map((_, index) => h(Form, { index }))) })
    const router = createRouter({ history: createMemoryHistory(), routes: [
        { path: '/post/:id', component: Page },
        { path: '/away', component: { render: () => null } },
    ] })
    await router.push('/post/1')
    await router.isReady()
    const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [pinia, router] } })
    wrappers.push(wrapper)
    return { forms, wrapper, router, confirm: useConfirmStore(), auth: useAuthStore() }
}

describe('comment draft protection', () => {
    it.each(['content', 'identity', 'sending'] as const)('rejects a pending decision when %s changes', async (field) => {
        const { forms: [form], confirm } = await setup()
        const decision = form.confirmDiscard!()
        expect(confirm.isOpen).toBe(true)
        if (field === 'content') form.content.value = 'Newer draft'
        if (field === 'identity') form.identity.value = 'post:2'
        if (field === 'sending') form.sending.value = true
        expect(confirm.isOpen).toBe(false)
        expect(await decision).toBe(false)
    })

    it('keeps every draft when a second form rejects route leave', async () => {
        const { router, forms, confirm } = await setup(2)
        const navigation = router.push('/away')
        await flushPromises()
        expect(confirm.isOpen).toBe(true)
        confirm.confirm()
        await flushPromises()
        expect(confirm.isOpen).toBe(true)
        confirm.cancel()
        await navigation
        expect(router.currentRoute.value.path).toBe('/post/1')
        expect(forms.map(form => form.content.value)).toEqual(['Private draft', 'Private draft'])
    })

    it('guards a different post on the reused route but allows a same-post hash', async () => {
        const { router, confirm } = await setup()
        await router.push('/post/1#comment-5')
        expect(confirm.isOpen).toBe(false)
        const navigation = router.push('/post/2')
        await flushPromises()
        expect(confirm.isOpen).toBe(true)
        confirm.cancel()
        await navigation
        expect(router.currentRoute.value.path).toBe('/post/1')
    })

    it('allows untouched edit content and whitespace-only new input without prompting', async () => {
        const { forms: [form], confirm } = await setup()
        form.initial.value = 'Private draft'
        expect(await form.confirmDiscard!()).toBe(true)
        form.initial.value = ''
        form.content.value = ' \n '
        expect(await form.confirmDiscard!()).toBe(true)
        expect(confirm.isOpen).toBe(false)
    })

    it('blocks native unload for a dirty or pending form', async () => {
        const { forms: [form] } = await setup()
        const unload = () => {
            const event = new Event('beforeunload', { cancelable: true })
            window.dispatchEvent(event)
            return event.defaultPrevented
        }
        expect(unload()).toBe(true)
        form.content.value = ''
        expect(unload()).toBe(false)
        form.sending.value = true
        expect(unload()).toBe(true)
        expect(await form.confirmDiscard!()).toBe(false)
    })

    it.each(['session', 'unmount'] as const)('cancels the confirmation after %s changes', async (boundary) => {
        const { forms: [form], confirm, auth, wrapper } = await setup()
        const decision = form.confirmDiscard!()
        if (boundary === 'session') auth.sessionGeneration++
        else wrapper.unmount()
        expect(confirm.isOpen).toBe(false)
        expect(await decision).toBe(false)
    })

    it('allows token rotation and keeps input until the approved close actually happens', async () => {
        const { forms: [form], confirm, auth } = await setup()
        const decision = form.confirmDiscard!()
        auth.accessToken = 'rotated-test-token'
        confirm.confirm()
        expect(await decision).toBe(true)
        expect(form.content.value).toBe('Private draft')
    })
})
