import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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

vi.mock('@/composables/useAdmin', () => ({
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

function mountModal(user: { id: number; name?: string; displayName?: string; nickname?: string; email?: string }) {
    return mount(SanctionModal, {
        props: {
            isOpen: true,
            user
        },
        global: {
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
})
