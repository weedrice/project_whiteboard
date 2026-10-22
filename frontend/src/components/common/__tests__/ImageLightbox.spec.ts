import { computed, defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import BaseModal from '../ui/BaseModal.vue'
import ImageLightbox from '../ui/ImageLightbox.vue'
import { usePostDetailKeyboardShortcuts } from '@/features/board/posts/detail/usePostDetailKeyboardShortcuts'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string, values?: { current: number; total: number }) =>
    values ? `${key}:${values.current}/${values.total}` : key }),
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
  it('preserves known dimensions and announces the current image state', async () => {
    const wrapper = mount(ImageLightbox, {
      props: { isOpen: true, images: [{ src: '/portrait.png', alt: 'Portrait', width: 300, height: 600 }] },
      global: { mocks: { $t: (key: string) => key }, stubs: { Teleport: true } },
    })
    await nextTick()
    const image = wrapper.get('img')
    expect(image.attributes('width')).toBe('300')
    expect(image.attributes('height')).toBe('600')
    expect(image.attributes('style')).toContain('aspect-ratio: 300 / 600')
    expect(wrapper.get('[role="status"]').text()).toBe('common.imageViewerLoading:1/1')
    Object.defineProperties(image.element, {
      complete: { configurable: true, value: true },
      naturalWidth: { configurable: true, value: 300 },
      naturalHeight: { configurable: true, value: 600 },
    })
    await image.trigger('load')
    expect(wrapper.get('[role="status"]').text()).toBe('common.imageViewerLoaded:1/1')
    wrapper.unmount()
  })

  it('retries the same URL once, preserves retry focus, and ignores stale events after navigation or close', async () => {
    const onLoaded = vi.fn()
    const wrapper = mount(ImageLightbox, {
      attachTo: document.body,
      props: { isOpen: true, images: [{ src: '/retry.png?signature=original', alt: 'First', onLoaded }, { src: '/next.png', alt: 'Next', onLoaded }] },
      global: { mocks: { $t: (key: string) => key } },
    })
    const currentImage = () => document.querySelector<HTMLImageElement>('.lightbox-image')!
    const status = () => document.querySelector('[role="status"]')!.textContent
    try {
      await nextTick()
      Object.defineProperty(currentImage(), 'complete', { configurable: true, value: true })
      currentImage().dispatchEvent(new Event('error'))
      await flushPromises()
      expect(status()).toBe('common.imageViewerError:1/2')
      const retry = Array.from(document.querySelectorAll('button')).find((button) => button.textContent?.trim() === 'common.imageRetry')!
      retry.focus()
      retry.click()
      await nextTick()
      const attempt = currentImage()
      expect(attempt.getAttribute('src')).toBe('/retry.png?signature=original')
      expect(retry.getAttribute('aria-disabled')).toBe('true')
      expect(document.activeElement).toBe(retry)
      retry.click()
      await nextTick()
      expect(currentImage()).toBe(attempt)
      expect(status()).toBe('common.imageViewerLoading:1/2')

      document.querySelector<HTMLButtonElement>('[aria-label="common.next"]')!.click()
      await nextTick()
      await nextTick()
      expect(status()).toBe('common.imageViewerLoading:2/2')
      attempt.dataset.imageState = 'loaded'
      attempt.dispatchEvent(new Event('imagestatechange'))
      await nextTick()
      expect(status()).toBe('common.imageViewerLoading:2/2')
      expect(document.activeElement).toBe(document.querySelector('[aria-label="common.close"]'))

      const nextImage = currentImage()
      await wrapper.setProps({ isOpen: false })
      nextImage.dataset.imageState = 'loaded'
      nextImage.dispatchEvent(new Event('imagestatechange'))
      await wrapper.setProps({ isOpen: true })
      expect(status()).toBe('common.imageViewerLoading:1/2')
      expect(onLoaded).not.toHaveBeenCalled()
    } finally {
      wrapper.unmount()
    }
  })

  it('moves retry focus to the close control after a successful retry', async () => {
    const onLoaded = vi.fn()
    const wrapper = mount(ImageLightbox, {
      attachTo: document.body,
      props: { isOpen: true, images: [{ src: '/retry.png', alt: 'Retry', onLoaded }] },
      global: { mocks: { $t: (key: string) => key } },
    })
    try {
      await nextTick()
      Object.defineProperty(document.querySelector('.lightbox-image')!, 'complete', { configurable: true, value: true })
      document.querySelector('.lightbox-image')!.dispatchEvent(new Event('error'))
      await flushPromises()
      const retry = Array.from(document.querySelectorAll('button')).find((button) => button.textContent?.trim() === 'common.imageRetry')!
      retry.focus()
      retry.click()
      await nextTick()
      const image = document.querySelector('.lightbox-image')!
      Object.defineProperties(image, {
        complete: { configurable: true, value: true },
        naturalWidth: { configurable: true, value: 400 },
      })
      image.dispatchEvent(new Event('load'))
      await nextTick()
      await nextTick()
      expect(document.querySelector('[role="status"]')!.textContent).toBe('common.imageViewerLoaded:1/1')
      expect(document.activeElement).toBe(document.querySelector('[aria-label="common.close"]'))
      expect(document.body.textContent).not.toContain('common.imageRetry')
      expect(onLoaded).toHaveBeenCalledTimes(1)
    } finally {
      wrapper.unmount()
    }
  })

  it('only zooms a decoded image and resets when navigating to the next image', async () => {
    const wrapper = mount(ImageLightbox, {
      props: {
        isOpen: true,
        images: [{ src: '/one.png', alt: 'First' }, { src: '/two.png', alt: 'Second' }],
      },
      global: { mocks: { $t: (key: string) => key }, stubs: { Teleport: true } },
    })
    let image = wrapper.get('img')
    image.element.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }))
    await nextTick()
    expect(image.attributes('style')).toContain('scale(1)')
    Object.defineProperties(image.element, {
      complete: { configurable: true, value: true },
      naturalWidth: { configurable: true, value: 120 },
    })
    await image.trigger('load')
    expect(image.attributes('data-image-state')).toBe('loaded')
    image.element.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }))
    await nextTick()
    expect(wrapper.get('img').attributes('style')).toContain('scale(2)')

    image = wrapper.get('img')
    Object.defineProperty(image.element, 'complete', { configurable: true, value: false })
    await wrapper.get('[aria-label="common.next"]').trigger('click')
    image = wrapper.get('img')
    expect(image.attributes('data-image-state')).toBe('loading')
    expect(image.attributes('style')).toContain('scale(1)')
    image.element.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }))
    await nextTick()
    expect(image.attributes('style')).toContain('scale(1)')
    wrapper.unmount()
  })

  it('resets double-click zoom when the current source is replaced', async () => {
    const wrapper = mount(ImageLightbox, {
      attachTo: document.body,
      props: { isOpen: true, images: [{ src: '/one.png', alt: 'First' }] },
      global: { mocks: { $t: (key: string) => key } },
    })
    try {
      await nextTick()
      const image = document.querySelector<HTMLImageElement>('.lightbox-image')!
      Object.defineProperties(image, {
        complete: { configurable: true, value: true }, naturalWidth: { configurable: true, value: 600 },
      })
      image.dispatchEvent(new Event('load'))
      await nextTick()
      expect(document.querySelector<HTMLImageElement>('.lightbox-image')!.dataset.imageState).toBe('loaded')
      image.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }))
      await nextTick()
      expect(document.querySelector<HTMLImageElement>('.lightbox-image')!.style.transform).toContain('scale(2)')
      await wrapper.setProps({ images: [{ src: '/changed.png', alt: 'Changed' }] })
      expect(document.querySelector<HTMLImageElement>('.lightbox-image')!.style.transform).toContain('scale(1)')
      expect(document.querySelector<HTMLImageElement>('.lightbox-image')!.dataset.imageState).toBe('loading')
    } finally {
      wrapper.unmount()
    }
  })

  it('requests authenticated entries on selection and keeps retry focus across blob replacement', async () => {
    const requestLoad = vi.fn((retry?: boolean) => {
      if (retry) entries.value = [{ ...first, failed: false, pending: true }, second]
    })
    const nextRequest = vi.fn()
    const first = { id: 'protected:1', src: '', alt: 'Protected', failed: true, requestLoad }
    const second = { id: 'protected:2', src: '', alt: 'Next', pending: true, requestLoad: nextRequest }
    const entries = ref([first, second])
    const host = defineComponent({
      setup: () => () => h(ImageLightbox, { isOpen: true, images: entries.value }),
    })
    const wrapper = mount(host, {
      attachTo: document.body,
      global: { mocks: { $t: (key: string) => key } },
    })
    const currentImage = () => document.querySelector<HTMLImageElement>('.lightbox-image')!
    try {
      await flushPromises()
      expect(requestLoad).toHaveBeenCalledExactlyOnceWith()
      expect(nextRequest).not.toHaveBeenCalled()
      expect(currentImage().hasAttribute('src')).toBe(false)
      const retry = Array.from(document.querySelectorAll('button')).find((button) => button.textContent?.trim() === 'common.imageRetry')!
      retry.focus()
      retry.click()
      await nextTick()
      expect(requestLoad).toHaveBeenLastCalledWith(true)
      expect(currentImage().dataset.imageState).toBe('loading')
      expect(document.activeElement).toBe(retry)
      retry.click()
      expect(requestLoad).toHaveBeenCalledTimes(2)
      entries.value = [{ ...first, src: 'blob:loaded', failed: false, pending: false }, second]
      await nextTick()
      expect(document.activeElement).toBe(retry)
      Object.defineProperties(currentImage(), {
        complete: { configurable: true, value: true }, naturalWidth: { configurable: true, value: 600 },
      })
      currentImage().dispatchEvent(new Event('load'))
      await nextTick()
      await nextTick()
      expect(document.activeElement).toBe(document.querySelector('[aria-label="common.close"]'))
      document.querySelector<HTMLButtonElement>('[aria-label="common.next"]')!.click()
      await nextTick()
      expect(nextRequest).toHaveBeenCalledExactlyOnceWith()
      expect(currentImage().hasAttribute('src')).toBe(false)
      expect(currentImage().dataset.imageState).toBe('loading')
    } finally {
      wrapper.unmount()
    }
  })

  it('ignores arrow keys while another dialog is on top', async () => {
    const lightbox = mount(ImageLightbox, {
      attachTo: document.body,
      props: { isOpen: true, images: [{ src: '/one.png', alt: 'First' }, { src: '/two.png', alt: 'Second' }] },
      global: { mocks: { $t: (key: string) => key } },
    })
    const modal = mount(BaseModal, {
      attachTo: document.body,
      props: { isOpen: true, title: 'Top dialog' },
      global: { mocks: { $t: (key: string) => key }, stubs: { BaseButton: BaseButtonStub } },
    })
    try {
      await nextTick()
      await nextTick()
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }))
      expect(lightbox.emitted('update:index')).toBeUndefined()
      await modal.setProps({ isOpen: false })
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }))
      expect(lightbox.emitted('update:index')).toEqual([[1]])
    } finally {
      modal.unmount()
      lightbox.unmount()
    }
  })

  it.each([true, false])('closes only the image viewer on Escape (initially open: %s)', async (initiallyOpen) => {
    const isOpen = ref(initiallyOpen)
    const goToList = vi.fn()
    const handleLike = vi.fn()
    const host = document.createElement('div')
    document.body.append(host)
    const Page = defineComponent({
      setup() {
        usePostDetailKeyboardShortcuts({
          router: { push: vi.fn() } as never,
          authStore: { isAuthenticated: true },
          postView: computed(() => ({ postId: 1 }) as never),
          canEdit: computed(() => true),
          isReportModalOpen: ref(false),
          buildEditRoute: () => '/edit',
          scrollToComments: vi.fn(),
          goToList,
          handleLike,
          handleBookmark: vi.fn(),
          handleShare: vi.fn(),
          handleCopyUrl: vi.fn(),
        })
        return () => isOpen.value ? h(ImageLightbox, {
          isOpen: true,
          images: [{ src: '/one.png', alt: 'First image' }],
          onClose: () => { isOpen.value = false },
        }) : h('button', 'Open image')
      },
    })
    const wrapper = mount(Page, {
      attachTo: host,
      global: { mocks: { $t: (key: string) => key } },
    })
    try {
      await nextTick()
      isOpen.value = true
      await nextTick()
      await nextTick()
      const closeButton = document.querySelector<HTMLButtonElement>('[role="dialog"] button')!
      closeButton.focus()
      closeButton.dispatchEvent(new KeyboardEvent('keydown', { key: 'l', bubbles: true, cancelable: true }))
      expect(handleLike).not.toHaveBeenCalled()
      closeButton.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
      await nextTick()
      expect(isOpen.value).toBe(false)
      expect(document.querySelector('[role="dialog"]')).toBeNull()
      expect(goToList).not.toHaveBeenCalled()

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
      expect(goToList).toHaveBeenCalledOnce()
    } finally {
      wrapper.unmount()
      host.remove()
    }
  })

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
