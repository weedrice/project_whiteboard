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
      props: { isOpen: true, images: [{ src: '/image.png', alt: 'Original alt' }], title: 'Preview' },
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

  it('traps focus while open, closes on Escape, and restores the opener', async () => {
    const opener = document.createElement('button')
    const host = document.createElement('div')
    document.body.append(opener, host)
    opener.focus()
    const lightbox = mount(ImageLightbox, {
      attachTo: host,
      props: {
        isOpen: true,
        images: [
          { src: '/one.png', alt: 'First image' },
          { src: '/two.png', alt: 'Second image' },
        ],
        title: 'Preview',
      },
      global: { mocks: { $t: (key: string) => key }, stubs: { Teleport: true } },
    })
    await nextTick()
    await nextTick()

    const dialog = lightbox.get('[role="dialog"]')
    const buttons = dialog.findAll('button')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.get('img').attributes('alt')).toBe('First image')
    expect(document.activeElement).toBe(buttons[0].element)

    ;(buttons.at(-1)!.element as HTMLButtonElement).focus()
    const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
    document.dispatchEvent(tab)
    expect(tab.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(buttons[0].element)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await nextTick()
    expect(lightbox.emitted('close')).toHaveLength(1)

    await lightbox.setProps({ isOpen: false })
    await nextTick()
    expect(document.activeElement).toBe(opener)
    lightbox.unmount()
    opener.remove()
    host.remove()
  })
})
