import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostVersionHistoryModal from '@/components/board/PostVersionHistoryModal.vue'

vi.mock('@/utils/date', () => ({
  formatDate: () => '2026-07-14 10:00',
}))

const BaseModalStub = {
  props: ['isOpen', 'title'],
  emits: ['close'],
  template: '<section v-if="isOpen"><h2>{{ title }}</h2><slot /></section>',
}

describe('PostVersionHistoryModal', () => {
  it('renders version summaries as a timeline', () => {
    const wrapper = mount(PostVersionHistoryModal, {
      props: {
        isOpen: true,
        isLoading: false,
        isError: false,
        versions: [
          { versionId: 2, actionType: 'MODIFY', title: 'Changed title', createdAt: '2026-07-14T10:00:00' },
          { versionId: 1, actionType: 'CREATE', title: 'Original title', createdAt: '2026-07-13T10:00:00' },
        ],
      },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { BaseModal: BaseModalStub },
      },
    })

    expect(wrapper.get('[data-testid="post-version-timeline"]').text()).toContain('Changed title')
    expect(wrapper.text()).toContain('수정')
    expect(wrapper.text()).toContain('2026-07-14 10:00')
  })

  it('shows empty and retryable error states', async () => {
    const wrapper = mount(PostVersionHistoryModal, {
      props: { isOpen: true, isLoading: false, isError: false, versions: [] },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { BaseModal: BaseModalStub },
      },
    })

    expect(wrapper.text()).toContain('표시할 수정 이력이 없습니다.')
    await wrapper.setProps({ isError: true })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
