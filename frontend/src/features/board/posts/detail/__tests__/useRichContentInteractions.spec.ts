import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import type { Router } from 'vue-router'
import { createImageStateController } from '@/utils/imageState'
import { useRichContentInteractions } from '../useRichContentInteractions'

const cleanup: Array<() => void> = []
afterEach(() => {
  cleanup.splice(0).reverse().forEach((dispose) => dispose())
  vi.restoreAllMocks()
})

function setupMounted(html: string) {
  const frames = new Map<number, FrameRequestCallback>()
  let nextFrame = 0
  vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
    frames.set(++nextFrame, callback)
    return nextFrame
  })
  vi.spyOn(window, 'cancelAnimationFrame').mockImplementation((id) => { frames.delete(id) })
  const isDisabled = ref(false)
  const getImageOpenLabel = vi.fn((alt: string, index: number) => `${alt} ${index}`)
  let interactions!: ReturnType<typeof useRichContentInteractions>
  const wrapper = mount(defineComponent({
    setup() {
      interactions = useRichContentInteractions({
        isDisabled,
        router: { push: vi.fn() } as unknown as Router,
        getCodeCopyLabels: () => ({ copy: 'Copy', copied: 'Copied', failed: 'Failed' }),
        getImageOpenLabel,
      })
      return () => h('article', { ref: interactions.articleRef }, [
        h('div', { class: 'nv-rich-content', innerHTML: html }),
      ])
    },
  }))
  cleanup.push(() => wrapper.unmount())
  function flushFrame() {
    const callbacks = [...frames.values()]
    frames.clear()
    callbacks.forEach((callback) => callback(0))
  }
  return { ...interactions, wrapper, article: wrapper.element as HTMLElement, isDisabled, getImageOpenLabel, frames, flushFrame }
}

function setup(html: string) {
  const isDisabled = ref(false)
  const interactions = useRichContentInteractions({
    isDisabled,
    router: { push: vi.fn() } as unknown as Router,
    getCodeCopyLabels: () => ({ copy: 'Copy', copied: 'Copied', failed: 'Failed' }),
    getImageOpenLabel: (alt) => alt,
  })
  const article = document.createElement('article')
  article.innerHTML = '<div class="nv-rich-content">' + html + '</div>'
  interactions.articleRef.value = article
  article.addEventListener('click', interactions.handleContentClick)
  article.addEventListener('keydown', interactions.handleContentKeydown)
  return { ...interactions, article, isDisabled }
}

describe('useRichContentInteractions', () => {
  it('batches cached image completion and updates only changed images with their current indices', () => {
    const state = setupMounted('<img src="/cached.svg" alt="Photo">'.repeat(500))
    const images = state.article.querySelectorAll('img')
    const query = vi.spyOn(state.article, 'querySelectorAll')
    images.forEach((image) => {
      Object.defineProperty(image, 'complete', { configurable: true, value: true })
      Object.defineProperty(image, 'naturalWidth', { configurable: true, value: 400 })
      const controller = createImageStateController(image, () => 'Failed')
      cleanup.push(controller.destroy)
    })
    expect(state.frames.size).toBe(1)
    expect(query).not.toHaveBeenCalled()
    state.flushFrame()
    expect(query).toHaveBeenCalledTimes(1)
    expect(state.getImageOpenLabel).toHaveBeenCalledTimes(500)
    expect(images[499]!.getAttribute('aria-label')).toBe('Photo 499')

    state.getImageOpenLabel.mockClear()
    const changedImage = images[499]!
    changedImage.dataset.imageState = 'error'
    changedImage.setAttribute('aria-label', 'Failed')
    changedImage.dispatchEvent(new Event('imagestatechange', { bubbles: true }))
    state.flushFrame()
    expect(changedImage.tabIndex).toBe(-1)
    expect(changedImage.hasAttribute('role')).toBe(false)
    expect(changedImage.getAttribute('aria-label')).toBe('Failed')
    expect(state.getImageOpenLabel).not.toHaveBeenCalled()

    changedImage.dataset.imageState = 'loaded'
    changedImage.dispatchEvent(new Event('imagestatechange', { bubbles: true }))
    state.article.querySelector('.nv-rich-content')!.prepend(changedImage)
    state.flushFrame()
    expect(changedImage.tabIndex).toBe(0)
    expect(changedImage.getAttribute('role')).toBe('button')
    expect(state.getImageOpenLabel).toHaveBeenCalledExactlyOnceWith('Photo', 0)
  })

  it('discards removed or hidden content and cancels queued work on full preparation and unmount', () => {
    const state = setupMounted('<img src="/image.svg" data-image-state="loaded">')
    const image = state.article.querySelector('img')!
    const notify = () => image.dispatchEvent(new Event('imagestatechange', { bubbles: true }))
    notify()
    state.isDisabled.value = true
    state.flushFrame()
    expect(state.getImageOpenLabel).not.toHaveBeenCalled()

    state.isDisabled.value = false
    notify()
    image.remove()
    state.flushFrame()
    expect(state.getImageOpenLabel).not.toHaveBeenCalled()

    state.article.querySelector('.nv-rich-content')!.append(image)
    notify()
    state.prepareImagesForInteraction()
    expect(state.frames.size).toBe(0)
    expect(state.getImageOpenLabel).toHaveBeenCalledTimes(1)
    notify()
    expect(state.frames.size).toBe(1)
    state.wrapper.unmount()
    expect(state.frames.size).toBe(0)
    notify()
    state.flushFrame()
    expect(state.getImageOpenLabel).toHaveBeenCalledTimes(1)
  })

  it('excludes pending and failed images from focus and the lightbox', () => {
    const state = setup('<img src="/pending.png" data-image-state="loading"><img src="/failed.png" data-image-state="error" aria-label="Failed"><img src="/ready.png" data-image-state="loaded">')
    state.prepareImagesForInteraction()
    const images = state.article.querySelectorAll('img')
    expect(images[0]!.tabIndex).toBe(-1)
    expect(images[1]!.getAttribute('aria-label')).toBe('Failed')
    images[0]!.click()
    images[1]!.click()
    expect(state.lightboxOpen.value).toBe(false)
    images[2]!.click()
    expect(state.lightboxImages.value).toHaveLength(1)
    images[0]!.dataset.imageState = 'loaded'
    state.prepareImagesForInteraction()
    expect(images[0]!.tabIndex).toBe(0)
  })
  it('opens an authenticated image hydrated after the initial lightbox render', () => {
    const state = setup('<img alt="protected" data-authenticated-file-src="/files/42">')
    expect(state.lightboxImages.value).toEqual([])
    const image = state.article.querySelector('img')!
    image.src = 'blob:authenticated'
    image.click()
    expect(state.lightboxOpen.value).toBe(true)
    expect(state.lightboxImages.value).toEqual([{ src: 'blob:authenticated', alt: 'protected' }])
    expect(state.lightboxIndex.value).toBe(0)
  })

  it('refreshes current image sources and selects the clicked occurrence on each opening', () => {
    const state = setup('<img src="blob:old" alt="first"><img src="blob:old" alt="second">')
    const images = state.article.querySelectorAll('img')
    images[1]!.click()
    expect(state.lightboxIndex.value).toBe(1)
    state.lightboxOpen.value = false
    images[1]!.src = 'blob:new'
    images[1]!.click()
    expect(state.lightboxImages.value[1]).toEqual({ src: 'blob:new', alt: 'second' })
    expect(state.lightboxOpen.value).toBe(true)
  })

  it.each(['Enter', ' '])('opens hydrated images through the %s key', (key) => {
    const state = setup('<img><img alt="loaded">')
    expect(state.lightboxImages.value).toEqual([])
    const image = state.article.querySelectorAll('img')[1]!
    image.src = 'blob:loaded'
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
    image.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    expect(state.lightboxOpen.value).toBe(true)
    expect(state.lightboxIndex.value).toBe(0)
    expect(state.lightboxImages.value).toHaveLength(1)
  })

  it('does not open unloaded images or images behind the spoiler guard', () => {
    const state = setup('<img alt="protected">')
    const image = state.article.querySelector('img')!
    image.click()
    expect(state.lightboxOpen.value).toBe(false)
    image.src = 'blob:protected'
    state.isDisabled.value = true
    image.click()
    image.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    expect(state.lightboxOpen.value).toBe(false)
  })
})
