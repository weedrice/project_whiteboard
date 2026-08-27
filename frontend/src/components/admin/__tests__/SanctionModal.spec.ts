import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import {
    BaseButtonStub,
    BaseInputStub,
    BaseModalStub,
    BaseTextareaStub,
    PassThroughStub,
} from '@/test/vue-test-helpers'
import SanctionModal from '../SanctionModal.vue'

const mocks = vi.hoisted(() => ({
    addToast: vi.fn(),
    sanctionUser: vi.fn(),
    sanctionTypes: ['WARNING', 'MUTE', 'BAN'],
    sanctionTypesLoading: false,
    sanctionTypesValidating: false,
    sanctionTypesError: false,
}))

vi.mock('@/composables/useCommonCodeDetails', async () => {
    const { computed } = await vi.importActual<typeof import('vue')>('vue')
    return {
        COMMON_CODE_TYPES: { SANCTION_TYPE: 'SANCTION_TYPE' },
        useStrictSupportedCommonCodeValues: () => ({
            values: computed(() => mocks.sanctionTypesLoading
                ? []
                : mocks.sanctionTypes),
            isReady: computed(() => !mocks.sanctionTypesLoading
                && !mocks.sanctionTypesValidating
                && !mocks.sanctionTypesError),
            isLoading: computed(() => mocks.sanctionTypesLoading),
            isValidating: computed(() => mocks.sanctionTypesLoading || mocks.sanctionTypesValidating),
            isError: computed(() => mocks.sanctionTypesError),
        }),
    }
})

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
                BaseTextarea: BaseTextareaStub
            }
        }
    })
}

describe('SanctionModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.sanctionUser.mockResolvedValue(undefined)
        mocks.sanctionTypes = ['WARNING', 'MUTE', 'BAN']
        mocks.sanctionTypesLoading = false
        mocks.sanctionTypesValidating = false
        mocks.sanctionTypesError = false
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

    it('uses the active sanction common code order', () => {
        mocks.sanctionTypes = ['BAN', 'WARNING']

        const wrapper = mountModal({ id: 7, name: 'Reported User' })
        const options = wrapper.get('select#sanction-type').findAll('option')

        expect(options.map((option) => option.attributes('value'))).toEqual(['BAN', 'WARNING'])
    })

    it('selects an active fallback when warning is inactive', async () => {
        mocks.sanctionTypes = ['BAN']
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).toHaveBeenCalledWith(expect.objectContaining({
            targetUserId: 7,
            type: 'BAN',
        }))
    })

    it('fails closed when sanction type common codes cannot be loaded', async () => {
        mocks.sanctionTypesError = true
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        expect(wrapper.get('select#sanction-type').attributes('disabled')).toBeDefined()
        await wrapper.get('form').trigger('submit')

        expect(mocks.sanctionUser).not.toHaveBeenCalled()
    })

    it('locks cancellation and inputs while a sanction request is pending', async () => {
        let resolveSanction!: () => void
        mocks.sanctionUser.mockReturnValueOnce(new Promise<void>((resolve) => { resolveSanction = resolve }))
        const wrapper = mountModal({ id: 7, name: 'Reported User' })

        await wrapper.get('form').trigger('submit')
        await flushPromises()

        const cancelButton = wrapper.findAll('button').find((button) => button.text() === 'admin.sanction.cancel')
        expect(cancelButton?.attributes('disabled')).toBeDefined()
        expect(wrapper.get('select#sanction-type').attributes('disabled')).toBeDefined()
        await cancelButton?.trigger('click')
        expect(wrapper.emitted('close')).toBeUndefined()

        resolveSanction()
        await flushPromises()
        expect(wrapper.emitted('close')).toHaveLength(1)
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
