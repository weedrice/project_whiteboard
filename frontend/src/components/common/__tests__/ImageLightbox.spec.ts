import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import BaseModal from '../ui/BaseModal.vue'
import ImageLightbox from '../ui/ImageLightbox.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const BaseButtonStub = defineComponent({
  emits: ['click'],
  setup(_, { emit, slots }) {
    return () => h('button', { type: 'button', onClick: () => emit('click') }, slots.default?.())
  },
})

afterEach(() => {
  document.body.style.overflow = ''
})

describe('ImageLightbox', () => {
  it('shares the body scroll lock with an underlying modal', async () => {
    const modal = mount(BaseModal, {
      props: { isOpen: true, title: 'Modal' },
      global: { mocks: { $t: (key: string) => key }, stubs: { BaseButton: BaseButtonStub, Teleport: true } },
    })
    const lightbox = mount(ImageLightbox, {
      props: { isOpen: true, images: ['/image.png'], title: 'Preview' },
      global: { mocks: { $t: (key: string) => key }, stubs: { Teleport: true } },
    })
    await nextTick()

    expect(document.body.style.overflow).toBe('hidden')

    await lightbox.setProps({ isOpen: false })
    expect(document.body.style.overflow).toBe('hidden')

    await modal.setProps({ isOpen: false })
    expect(document.body.style.overflow).toBe('')

    lightbox.unmount()
    modal.unmount()
  })
})
