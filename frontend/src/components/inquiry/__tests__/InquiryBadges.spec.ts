import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import InquiryPriorityBadge from '../InquiryPriorityBadge.vue'
import InquiryStatusBadge from '../InquiryStatusBadge.vue'
import type { InquiryPriority, InquiryStatus } from '@/types/inquiry'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('inquiry badges', () => {
  it.each([
    ['NEW', 'info'],
    ['IN_PROGRESS', 'warning'],
    ['RESOLVED', 'success'],
    ['CLOSED', 'gray'],
  ] as const)('maps status %s to the %s badge variant', (status, variant) => {
    const wrapper = mount(InquiryStatusBadge, { props: { status: status as InquiryStatus } })

    expect(wrapper.getComponent(BaseBadge).props('variant')).toBe(variant)
    expect(wrapper.get('[data-inquiry-status]').attributes('data-inquiry-status')).toBe(status)
  })

  it.each([
    ['URGENT', 'danger'],
    ['HIGH', 'warning'],
    ['NORMAL', 'gray'],
  ] as const)('maps priority %s to the %s badge variant', (priority, variant) => {
    const wrapper = mount(InquiryPriorityBadge, { props: { priority: priority as InquiryPriority } })

    expect(wrapper.getComponent(BaseBadge).props('variant')).toBe(variant)
  })
})
