import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { Router } from 'vue-router'
import { createImageStateController } from '@/utils/imageState'
import { useRichContentInteractions } from '../useRichContentInteractions'
import { createAuthenticatedImageLoader } from '@/utils/authenticatedImageLoader'
import { createDeferred } from '@/test/async'

const get = vi.hoisted(() => vi.fn())
vi.mock('@/api', () => ({ default: { get } }))

const cleanup: Array<() => void> = []
afterEach(() => {
  cleanup.splice(0).reverse().forEach((dispose) => dispose())
  vi.restoreAllMocks()
  get.mockReset()
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
      return () => h('article', {
        ref: interactions.articleRef,
        onClick: interactions.handleContentClick,
        onKeydown: interactions.handleContentKeydown,
      }, [
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
  it.each([
    ['href="#target"', 'loaded'],
    ['data-authenticated-file-href="/files/17"', 'loaded'],
    ['href="#target"', 'error'],
    ['data-authenticated-file-href="/files/17"', 'error'],
  ])(
    'respects linked images (%s, %s) without adding a second tab stop or opening the gallery', (attributes, imageState) => {
      const state = setup(`<a ${attributes}><img src="/linked.png" data-image-state="${imageState}" tabindex="0" role="button" aria-haspopup="dialog"></a><img src="/standalone.png">`)
      state.prepareImagesForInteraction()
      const [linked, standalone] = Array.from(state.article.querySelectorAll('img'))
      expect(linked!.hasAttribute('tabindex')).toBe(false)
      expect(linked!.hasAttribute('role')).toBe(false)
      expect(linked!.hasAttribute('aria-haspopup')).toBe(false)
      const click = new MouseEvent('click', { bubbles: true, cancelable: true })
      linked!.dispatchEvent(click)
      expect(click.defaultPrevented).toBe(false)
      const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      linked!.dispatchEvent(enter)
      expect(enter.defaultPrevented).toBe(false)
      expect(state.lightboxOpen.value).toBe(false)
      standalone!.click()
      expect(state.lightboxImages.value).toHaveLength(1)
      expect(state.lightboxImages.value[0]!.src).toBe(standalone!.src)
    },
  )

  it('keeps failed native images available on subsequent gallery openings', () => {
    const state = setupMounted('<img src="/ready.png" data-image-state="loaded"><img src="/slow.png" data-image-state="loading">')
    const images = state.article.querySelectorAll('img')
    images[0]!.click()
    state.lightboxIndex.value = 1
    images[1]!.dataset.imageState = 'error'
    images[1]!.dispatchEvent(new Event('imagestatechange', { bubbles: true }))
    state.flushFrame()
    expect(state.lightboxOpen.value).toBe(true)
    expect(state.lightboxIndex.value).toBe(1)
    expect(state.lightboxImages.value).toHaveLength(2)
    state.lightboxOpen.value = false
    images[0]!.click()
    expect(state.lightboxImages.value).toHaveLength(2)
  })

  it.each(['Enter', ' '])('opens a sole failed native image through %s without losing its error description', (key) => {
    const state = setup('<img src="/failed.png" alt="Photo" data-image-state="error" aria-label="Photo — Failed">')
    state.prepareImagesForInteraction()
    const image = state.article.querySelector('img')!
    expect(image.tabIndex).toBe(0)
    expect(image.getAttribute('role')).toBe('button')
    expect(image.getAttribute('aria-label')).toBe('Photo — Failed')
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
    image.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    expect(state.lightboxOpen.value).toBe(true)
    expect(state.lightboxImages.value).toEqual([{ src: image.src, alt: 'Photo', onLoaded: expect.any(Function) }])
  })

  it('retries the original native URL after gallery recovery and lets its own load determine success', () => {
    const state = setupMounted('<img src="/image.png?signature=unchanged" data-image-state="error">')
    const image = state.article.querySelector('img')!
    image.click()
    const callback = state.lightboxImages.value[0]!.onLoaded!
    state.prepareImagesForInteraction()
    expect(state.lightboxImages.value[0]!.onLoaded).toBe(callback)
    const setAttribute = vi.spyOn(image, 'setAttribute')
    callback()
    expect(setAttribute).toHaveBeenCalledExactlyOnceWith('src', '/image.png?signature=unchanged')
    expect(image.dataset.imageState).toBe('error')
    image.dataset.imageState = 'loaded'
    callback()
    expect(setAttribute).toHaveBeenCalledTimes(1)
  })

  it('keeps a recovering original pending until its own native load completes', async () => {
    const state = setupMounted('<img src="/image.png" alt="Photo">')
    const image = state.article.querySelector('img')!
    Object.defineProperties(image, {
      complete: { configurable: true, value: true },
      naturalWidth: { configurable: true, value: 0 },
    })
    const controller = createImageStateController(image, () => 'Failed')
    cleanup.push(controller.destroy)
    state.prepareImagesForInteraction()
    image.click()
    const callback = state.lightboxImages.value[0]!.onLoaded!
    Object.defineProperty(image, 'complete', { configurable: true, value: false })
    callback()
    await flushPromises()
    expect(image.dataset.imageState).toBe('loading')
    expect(image.getAttribute('aria-busy')).toBe('true')
    expect(image.naturalWidth).toBe(0)
    Object.defineProperties(image, {
      complete: { configurable: true, value: true },
      naturalWidth: { configurable: true, value: 400 },
    })
    image.dispatchEvent(new Event('load'))
    expect(image.dataset.imageState).toBe('loaded')
    expect(image.hasAttribute('aria-busy')).toBe(false)
  })

  it.each(['source', 'srcset', 'sizes', 'content', 'session', 'closed', 'reopened', 'disabled'])(
    'ignores native gallery recovery after %s changes', (boundary) => {
      const state = setupMounted('<img src="/image.png?signature=unchanged" data-image-state="error">')
      const image = state.article.querySelector('img')!
      image.click()
      const callback = state.lightboxImages.value[0]!.onLoaded!
      if (boundary === 'source') image.src = '/replacement.png'
      if (boundary === 'srcset') image.srcset = '/replacement.png 2x'
      if (boundary === 'sizes') image.sizes = '50vw'
      if (boundary === 'content') image.remove()
      if (boundary === 'session') state.article.dispatchEvent(new Event('authenticatedimagesreset'))
      if (boundary === 'closed' || boundary === 'reopened') state.lightboxOpen.value = false
      if (boundary === 'reopened') image.click()
      if (boundary === 'disabled') state.isDisabled.value = true
      const setAttribute = vi.spyOn(image, 'setAttribute')
      callback()
      expect(setAttribute).not.toHaveBeenCalled()
    },
  )

  it('opens and retries a sole failed authenticated image without a native source', async () => {
    get.mockRejectedValueOnce(new Error('Unavailable')).mockResolvedValue({ data: new Blob(['retry']) })
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:recovered')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const state = setupMounted('<img data-authenticated-file-src="/files/42" alt="Protected" data-image-state="loading">')
    const loader = createAuthenticatedImageLoader(state.article, (image, status) => {
      image.dataset.imageState = status
    })
    cleanup.push(loader.destroy)
    const image = state.article.querySelector('img')!
    // Request explicitly to keep this unit independent of IntersectionObserver delivery.
    const resource = (await import('@/utils/authenticatedImageLoader')).getAuthenticatedImageResource(image)!
    resource.requestLoad()
    await flushPromises()
    state.flushFrame()
    expect(image.dataset.imageState).toBe('error')
    expect(image.tabIndex).toBe(0)
    image.click()
    expect(state.lightboxOpen.value).toBe(true)
    expect(state.lightboxImages.value[0]).toMatchObject({ src: '', failed: true })
    state.lightboxImages.value[0]!.requestLoad!(true)
    await flushPromises()
    state.flushFrame()
    expect(get).toHaveBeenCalledTimes(2)
    expect(state.lightboxImages.value[0]).toMatchObject({ src: 'blob:recovered', failed: false })
  })

  it('updates pending authenticated gallery entries without losing selection and invalidates old callbacks', async () => {
    const response = createDeferred<{ data: Blob }>()
    get.mockReturnValueOnce(response.promise).mockResolvedValue({ data: new Blob(['retry']) })
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:gallery')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const state = setupMounted('<img src="/ready.png" data-image-state="loaded"><img data-authenticated-file-src="/files/42" data-image-state="loading" width="400" height="200">')
    const images = state.article.querySelectorAll('img')
    vi.spyOn(images[1]!, 'getBoundingClientRect').mockReturnValue({ top: 12000, bottom: 12200 } as DOMRect)
    const loader = createAuthenticatedImageLoader(state.article, (image, status) => {
      image.dataset.imageState = status === 'loaded' ? 'loading' : status
    })
    cleanup.push(loader.destroy)
    images[0]!.click()
    expect(state.lightboxImages.value).toHaveLength(2)
    const pending = state.lightboxImages.value[1]!
    expect(pending).toMatchObject({ src: '', pending: true, failed: false, width: 400, height: 200 })
    state.lightboxIndex.value = 1
    pending.requestLoad!()
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(1)
    response.resolve({ data: new Blob(['image']) })
    await flushPromises()
    state.flushFrame()
    expect(state.lightboxIndex.value).toBe(1)
    expect(state.lightboxImages.value[1]).toMatchObject({ id: pending.id, src: 'blob:gallery', pending: false })
    const unchanged = state.lightboxImages.value
    state.prepareImagesForInteraction()
    expect(state.lightboxImages.value).toBe(unchanged)
    images[1]!.dataset.imageState = 'error'
    images[1]!.dispatchEvent(new Event('imagestatechange', { bubbles: true }))
    state.flushFrame()
    expect(state.lightboxImages.value[1]!.failed).toBe(true)
    pending.requestLoad!(true)
    expect(state.lightboxImages.value[1]).toMatchObject({ src: '', pending: true, failed: false })
    await flushPromises()
    loader.destroy()
    expect(state.lightboxOpen.value).toBe(false)
    expect(state.lightboxImages.value).toEqual([])
    pending.requestLoad!(true)
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('closes an open gallery when the selected content is replaced', () => {
    const state = setupMounted('<img src="/ready.png" data-image-state="loaded">')
    state.article.querySelector('img')!.click()
    state.article.querySelector('.nv-rich-content')!.innerHTML = '<img src="/replacement.png">'
    state.prepareImagesForInteraction()
    expect(state.lightboxOpen.value).toBe(false)
    expect(state.lightboxImages.value).toEqual([])
  })

  it('carries natural or explicit dimensions into the gallery and omits unknown or invalid sizes', () => {
    const state = setup('<img src="/natural.png" width="20" height="10"><img src="/portrait.png" width="300" height="600" data-image-state="loading"><img src="/unknown.png"><img src="/invalid.png" width="-10" height="20">')
    const images = state.article.querySelectorAll('img')
    Object.defineProperties(images[0]!, {
      naturalWidth: { value: 800 },
      naturalHeight: { value: 400 },
    })
    images[0]!.click()
    expect(state.lightboxImages.value[0]).toMatchObject({ width: 800, height: 400 })
    expect(state.lightboxImages.value[1]).toMatchObject({ width: 300, height: 600 })
    expect(state.lightboxImages.value[2]).not.toHaveProperty('width')
    expect(state.lightboxImages.value[3]).not.toHaveProperty('width')
  })

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
    expect(changedImage.tabIndex).toBe(0)
    expect(changedImage.getAttribute('role')).toBe('button')
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

  it('keeps pending images in the gallery while preventing them from opening it directly', () => {
    const state = setup('<img src="/pending.png" data-image-state="loading"><img src="/failed.png" data-image-state="error" aria-label="Failed"><img src="/ready.png" data-image-state="loaded">')
    state.prepareImagesForInteraction()
    const images = state.article.querySelectorAll('img')
    expect(images[0]!.tabIndex).toBe(-1)
    expect(images[1]!.getAttribute('aria-label')).toBe('Failed')
    images[0]!.click()
    expect(state.lightboxOpen.value).toBe(false)
    images[1]!.click()
    expect(state.lightboxOpen.value).toBe(true)
    expect(state.lightboxIndex.value).toBe(1)
    images[2]!.click()
    expect(state.lightboxImages.value.map((image) => image.src)).toEqual([
      images[0]!.src,
      images[1]!.src,
      images[2]!.src,
    ])
    expect(state.lightboxIndex.value).toBe(2)
    images[0]!.dataset.imageState = 'loaded'
    state.prepareImagesForInteraction()
    expect(images[0]!.tabIndex).toBe(0)
  })

  it('uses currentSrc and skips empty sources while keeping failed images retryable', () => {
    const state = setup('<img src="" data-image-state="loading"><img src="   "><img data-image-state="loading"><img src="/failed.png" data-image-state="error"><img src="/fallback.png" data-image-state="loading" alt="responsive"><img src="/ready.png" data-image-state="loaded" alt="ready">')
    const images = state.article.querySelectorAll('img')
    Object.defineProperty(images[4]!, 'currentSrc', { value: 'https://images.example/responsive-large.png' })
    images[5]!.click()
    expect(state.lightboxImages.value).toEqual([
      { src: images[3]!.src, alt: '', onLoaded: expect.any(Function) },
      { src: 'https://images.example/responsive-large.png', alt: 'responsive' },
      { src: images[5]!.src, alt: 'ready' },
    ])
    expect(state.lightboxIndex.value).toBe(2)
  })

  it.each(['Enter', ' '])('does not open pending images through the %s key', (key) => {
    const state = setup('<img src="/pending.png" data-image-state="loading">')
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
    state.article.querySelector('img')!.dispatchEvent(event)
    expect(state.lightboxOpen.value).toBe(false)
    expect(event.defaultPrevented).toBe(false)
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
