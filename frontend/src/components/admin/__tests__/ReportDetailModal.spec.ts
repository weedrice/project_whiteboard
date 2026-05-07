import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import ReportDetailModal from '../ReportDetailModal.vue'
import type { Report } from '@/types'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key
    })
}))

vi.mock('@/utils/date', () => ({
    formatDate: (value: string) => value
}))

const BaseModalStub = defineComponent({
    props: {
        isOpen: Boolean,
        title: String
    },
    setup(props, { slots }) {
        return () => props.isOpen ? h('div', { 'data-test': 'modal' }, [
            h('h1', props.title),
            slots.default?.()
        ]) : null
    }
})

const BaseBadgeStub = defineComponent({
    setup(_, { slots }) {
        return () => h('span', slots.default?.())
    }
})

const baseReport: Report = {
    reportId: 1,
    reporterId: 2,
    reporterDisplayName: 'Reporter',
    targetType: 'POST',
    targetId: 10,
    targetUserId: 20,
    reasonType: 'SPAM',
    remark: null,
    status: 'PENDING',
    contents: null,
    targetDisplayName: null,
    targetLoginId: null,
    createdAt: '2026-05-07T00:00:00',
    updatedAt: '2026-05-07T00:00:00',
    adminId: null,
    processorUserId: null
}

function mountModal(report: Report) {
    return mount(ReportDetailModal, {
        props: {
            isOpen: true,
            report
        },
        global: {
            stubs: {
                BaseModal: BaseModalStub,
                BaseBadge: BaseBadgeStub
            }
        }
    })
}

describe('ReportDetailModal', () => {
    it('shows legacy report contents as the report reason', () => {
        const wrapper = mountModal({
            ...baseReport,
            contents: 'legacy reason',
            remark: null
        })

        expect(wrapper.text()).toContain('legacy reason')
    })

    it('falls back to remark when contents is missing', () => {
        const wrapper = mountModal({
            ...baseReport,
            contents: null,
            remark: 'legacy link or remark'
        })

        expect(wrapper.text()).toContain('legacy link or remark')
    })
})
