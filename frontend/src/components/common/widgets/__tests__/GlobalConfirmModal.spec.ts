import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import GlobalConfirmModal from '../GlobalConfirmModal.vue'
import { useConfirmStore } from '@/stores/confirm'
import { BaseModalStub } from '@/test/vue-test-helpers'

describe('GlobalConfirmModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders cancel and confirm as direct modal footer actions', () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const confirmStore = useConfirmStore()
    void confirmStore.open('message', 'title', 'confirm', 'cancel')

    const wrapper = mount(GlobalConfirmModal, {
      global: {
        plugins: [pinia],
        stubs: { BaseModal: BaseModalStub },
      },
    })
    const footer = wrapper.get('[data-test="modal-footer"]')

    expect(Array.from(footer.element.children).map((child) => child.tagName)).toEqual(['BUTTON', 'BUTTON'])
    expect(footer.findAll('button').map((button) => button.text())).toEqual(['cancel', 'confirm'])
    expect(wrapper.get('[data-test="modal-body"]').text()).toBe('message')
  })
})
