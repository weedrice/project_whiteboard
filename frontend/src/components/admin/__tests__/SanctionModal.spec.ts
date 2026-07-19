import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import {
    BaseButtonStub,
    BaseInputStub,
    BaseModalStub,
    BaseSelectStub,
    BaseTextareaStub,
    PassThroughStub,
} from '@/test/vue-test-helpers'
import SanctionModal from '../SanctionModal.vue'

const mocks = vi.hoisted(() => ({
    addToast: vi.fn(),
    sanctionUser: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key
    })
}))

vi.mock('@/features/admin/useAdmin', () => ({
    useAdmin: () => ({
        useSanctionUser: () => ({
            mutateAsync: mocks.sanctionUser,
            isPending: ref(false)
        })
    })
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: mocks.addToast
    })
}))

function mountModal(user: {
    id: number
    name?: string
    displayName?: string
    nickname?: string
    email?: string
    reportId?: number
    modalRevision?: number
    sessionGeneration?: number
}) {
    return mount(SanctionModal, {
        props: {
            isOpen: true,
            user
        },
        global: {
            plugins: [createPinia()],
            stubs: {
                BaseModal: BaseModalStub,
                AdminModalActions: PassThroughStub,
                BaseButton: BaseButtonStub,
                BaseInput: BaseInputStub,
                BaseSelect: BaseSelectStub,
                BaseTextarea: BaseTextareaStub
            }
        }
    })
}

describe('SanctionModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.sanctionUser.mockResolvedValue(undefined)
    })

    it('shows report target names when only name is provided', () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        expect(wrapper.text()).toContain('Reported User')
        expect(wrapper.text()).not.toContain('()')
    })

    it('shows email only when it exists', () => {
        const wrapper = mountModal({ id: 7, displayName: 'Target', email: 'target@test.com' })

        expect(wrapper.text()).toContain('Target (target@test.com)')
    })

    it('falls back to the reason when description is blank after trimming', async () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        await wrapper.get('select#reason').setValue('ABUSIVE_LANGUAGE')
        await wrapper.get('textarea#description').setValue('   ')
        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).toHaveBeenCalledWith(expect.objectContaining({
            targetUserId: 7,
            remark: 'ABUSIVE_LANGUAGE',
        }))
    })

    it('trims a custom sanction description before submitting', async () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        await wrapper.get('textarea#description').setValue('  repeated spam  ')
        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).toHaveBeenCalledWith(expect.objectContaining({
            targetUserId: 7,
            remark: 'repeated spam',
        }))
    })

    it('submits a warning without an end date by default', async () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).toHaveBeenCalledWith(expect.objectContaining({
            targetUserId: 7,
            type: 'WARNING',
            endDate: undefined,
        }))
    })

    it('requires a positive whole-day duration for mute sanctions', async () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        await wrapper.get('select#sanction-type').setValue('MUTE')
        await wrapper.get('input#duration').setValue('0')
        await wrapper.get('form').trigger('submit')
        expect(mocks.sanctionUser).not.toHaveBeenCalled()

        await wrapper.get('input#duration').setValue('3')
        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).toHaveBeenCalledWith(expect.objectContaining({
            targetUserId: 7,
            type: 'MUTE',
            endDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/),
        }))
    })

    it('submits a permanent ban when its duration is empty', async () => {
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        await wrapper.get('select#sanction-type').setValue('BAN')
        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).toHaveBeenCalledWith(expect.objectContaining({
            targetUserId: 7,
            type: 'BAN',
            endDate: undefined,
        }))
    })

    it('does not emit completion for a stale modal target', async () => {
        let resolveSanction!: () => void
        mocks.sanctionUser.mockReturnValueOnce(new Promise<void>(resolve => { resolveSanction = resolve }))
        const wrapper = mountModal({ id: 7, name: 'Old', reportId: 1, modalRevision: 1 })
        const pending = wrapper.get('form').trigger('submit')

        await wrapper.setProps({
            user: { id: 8, name: 'New', reportId: 2, modalRevision: 2 },
        })
        resolveSanction()
        await pending
        await flushPromises()

        expect(wrapper.emitted('sanctioned')).toBeUndefined()
        expect(mocks.addToast).not.toHaveBeenCalled()
    })

    it('does not submit a sanction opened by a different authentication session', async () => {
        const wrapper = mountModal({
            id: 7,
            name: 'Old session target',
            reportId: 1,
            modalRevision: 1,
            sessionGeneration: 1,
        })

        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).not.toHaveBeenCalled()
        expect(wrapper.emitted('sanctioned')).toBeUndefined()
    })
})
