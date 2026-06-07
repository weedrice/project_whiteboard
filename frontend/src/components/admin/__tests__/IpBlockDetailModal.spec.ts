import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { BaseModalStub, identityT } from '@/test/vue-test-helpers'
import IpBlockDetailModal from '../IpBlockDetailModal.vue'
import type { IpBlock } from '@/types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: identityT,
  }),
}))

vi.mock('@/utils/date', () => ({
  formatDate: (value: string) => `date:${value}`,
}))

const ipBlock: IpBlock = {
  ipAddress: '203.0.113.10',
  reason: 'Repeated spam\nfrom gateway',
  startDate: '2026-06-01T10:00:00',
  endDate: '2026-06-08T10:00:00',
  admin: {
    adminId: 7,
    displayName: 'Admin',
  },
}

function mountModal(block: IpBlock | null = ipBlock) {
  return mount(IpBlockDetailModal, {
    props: {
      isOpen: true,
      ipBlock: block,
    },
    global: {
      stubs: {
        BaseModal: BaseModalStub,
      },
    },
  })
}

describe('IpBlockDetailModal', () => {
  it('renders block detail information with formatted dates and reason text', () => {
    const wrapper = mountModal()

    expect(wrapper.text()).toContain('admin.security.detail.title')
    expect(wrapper.text()).toContain('203.0.113.10')
    expect(wrapper.text()).toContain('7')
    expect(wrapper.text()).toContain('date:2026-06-01T10:00:00')
    expect(wrapper.text()).toContain('date:2026-06-08T10:00:00')
    expect(wrapper.text()).toContain('Repeated spam')
    expect(wrapper.text()).toContain('from gateway')
  })

  it('keeps optional end date hidden when it is not present', () => {
    const wrapper = mountModal({
      ...ipBlock,
      endDate: null,
    })

    expect(wrapper.text()).toContain('203.0.113.10')
    expect(wrapper.text()).not.toContain('admin.security.detail.endDate')
  })

  it('renders the modal shell without detail content for an empty selection', () => {
    const wrapper = mountModal(null)

    expect(wrapper.find('[data-test="modal"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('admin.security.detail.title')
    expect(wrapper.text()).not.toContain('203.0.113.10')
  })
})
