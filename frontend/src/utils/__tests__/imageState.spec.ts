import { afterEach, describe, expect, it, vi } from 'vitest'
import { createImageStateController } from '@/utils/imageState'

const cleanup: Array<() => void> = []

function setup(src = '/image.png', complete = false, naturalWidth = 0) {
  const image = document.createElement('img')
  image.alt = 'Landscape'
  image.src = src
  Object.defineProperty(image, 'complete', { configurable: true, value: complete })
  Object.defineProperty(image, 'naturalWidth', { configurable: true, value: naturalWidth })
  document.body.append(image)
  const controller = createImageStateController(image, () => 'Unable to load image.')
  cleanup.push(() => { controller.destroy(); image.remove() })
  return { image, controller }
}

afterEach(() => {
  cleanup.splice(0).forEach((dispose) => dispose())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('image presentation lifecycle', () => {
  it('does not leave an image with an empty source loading indefinitely', () => {
    const { image, controller } = setup()
    image.removeAttribute('src')
    controller.refresh()
    expect(image.dataset.imageState).toBe('error')
    controller.refresh({ pending: true })
    expect(image.dataset.imageState).toBe('loading')
  })
  it('keeps pending images hidden until native loading finishes, including cache hits', () => {
    const { image } = setup()
    expect(image.dataset.imageState).toBe('loading')
    expect(image.getAttribute('aria-busy')).toBe('true')
    Object.defineProperty(image, 'complete', { value: true })
    Object.defineProperty(image, 'naturalWidth', { value: 640 })
    image.dispatchEvent(new Event('load'))
    expect(image.dataset.imageState).toBe('loaded')
    expect(image.hasAttribute('aria-busy')).toBe(false)
    expect(setup('/cached.png', true, 400).image.dataset.imageState).toBe('loaded')
  })

  it('finishes failures and restores accessible labels when the source changes', async () => {
    const { image } = setup()
    image.title = 'Original'
    image.dispatchEvent(new Event('error'))
    await Promise.resolve()
    expect(image.dataset.imageState).toBe('error')
    expect(image.getAttribute('aria-label')).toContain('Unable to load image.')
    expect(image.hasAttribute('aria-busy')).toBe(false)
    image.src = '/replacement.png'
    await Promise.resolve()
    expect(image.dataset.imageState).toBe('loading')
    expect(image.title).toBe('Original')
    expect(image.hasAttribute('aria-label')).toBe(false)
  })

  it('waits for a fallback selected by an existing error listener and stops on fallback failure', async () => {
    const { image } = setup()
    image.addEventListener('error', () => { image.src = '/fallback.png' }, { once: true })
    image.dispatchEvent(new Event('error'))
    await Promise.resolve()
    expect(image.dataset.imageState).toBe('loading')
    image.dispatchEvent(new Event('error'))
    await Promise.resolve()
    expect(image.dataset.imageState).toBe('error')
  })

  it('accepts API pending/failure states without an image source and cleans up listeners', async () => {
    const { image, controller } = setup()
    image.removeAttribute('src')
    controller.refresh({ pending: true })
    expect(image.dataset.imageState).toBe('loading')
    controller.refresh({ failed: true })
    expect(image.dataset.imageState).toBe('error')
    controller.destroy()
    image.src = '/another.png'
    image.dispatchEvent(new Event('load'))
    await Promise.resolve()
    expect(image.dataset.imageState).toBe('error')
  })

  it('reserves declared intrinsic proportions without modifying source or dimensions', () => {
    const { image, controller } = setup()
    image.width = 800
    image.height = 600
    controller.refresh()
    expect(decodeURIComponent(image.style.getPropertyValue('--nv-image-placeholder'))).toContain('width="800" height="600"')
    expect(image.getAttribute('src')).toBe('/image.png')
    expect(image.width).toBe(800)
    image.removeAttribute('width')
    image.removeAttribute('height')
    controller.refresh()
    expect(image.style.getPropertyValue('--nv-image-placeholder')).toBe('')
  })

  it('does not retain error labels when an authenticated image controller is recreated', async () => {
    const { image, controller } = setup()
    image.title = 'Original title'
    image.dispatchEvent(new Event('error'))
    await Promise.resolve()
    controller.destroy()
    expect(image.title).toBe('Original title')
    expect(image.hasAttribute('aria-label')).toBe(false)
    Object.defineProperty(image, 'complete', { value: true })
    Object.defineProperty(image, 'naturalWidth', { value: 640 })
    const replacement = createImageStateController(image, () => 'Unable to load image.')
    expect(image.dataset.imageState).toBe('loaded')
    expect(image.title).toBe('Original title')
    replacement.destroy()
  })
})


function controlErrorFrames() {
  const frames = new Map<number, FrameRequestCallback>()
  let nextFrame = 0
  vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
    frames.set(++nextFrame, callback)
    return nextFrame
  })
  vi.spyOn(window, 'cancelAnimationFrame').mockImplementation((id) => { frames.delete(id) })
  return {
    frames,
    flush() {
      const callbacks = [...frames.values()]
      frames.clear()
      callbacks.forEach((callback) => callback(0))
    },
  }
}

describe('batched error presentation', () => {
  it('reads all failed image sizes before writing any localized error symbols', () => {
    const frame = controlErrorFrames()
    const operations: string[] = []
    const images = Array.from({ length: 500 }, (_, index) => {
      const { image, controller } = setup(`/missing-${index}.png`, true)
      Object.defineProperties(image, {
        clientWidth: { get: () => { operations.push('read'); return 320 } },
        clientHeight: { get: () => { operations.push('read'); return 180 } },
      })
      const setProperty = image.style.setProperty.bind(image.style)
      vi.spyOn(image.style, 'setProperty').mockImplementation((name, value, priority) => {
        if (name === '--nv-image-error-symbol') operations.push('write')
        setProperty(name, value, priority)
      })
      controller.refresh()
      return image
    })
    expect(frame.frames.size).toBe(1)
    expect(operations).toEqual([])
    expect(images[0]!.getAttribute('aria-label')).toContain('Unable to load image.')
    frame.flush()
    expect(operations).toEqual([...Array<string>(1000).fill('read'), ...Array<string>(500).fill('write')])
    expect(decodeURIComponent(images[0]!.style.getPropertyValue('--nv-image-error-symbol'))).toContain('Unable to load image.')
    expect(decodeURIComponent(images[0]!.style.getPropertyValue('--nv-image-error-symbol'))).toContain('#3a3a3a')
    expect(decodeURIComponent(images[0]!.style.getPropertyValue('--nv-image-error-symbol-dark'))).toContain('#c8c7c2')
  })

  it('cancels errors on recovery or disposal and does not decorate a new source or removed image', async () => {
    const frame = controlErrorFrames()
    const entries = Array.from({ length: 4 }, () => setup('/failed.png', true))
    entries.forEach(({ image }) => Object.defineProperties(image, {
      clientWidth: { get: () => 320 },
      clientHeight: { get: () => 180 },
    }))
    const recovered = entries[0]!
    Object.defineProperty(recovered.image, 'naturalWidth', { value: 320 })
    recovered.image.dispatchEvent(new Event('load'))
    entries[1]!.controller.destroy()
    entries[2]!.image.src = '/replacement.png'
    entries[3]!.image.remove()
    // Flush before the source MutationObserver so the queued source guard is exercised.
    frame.flush()
    entries.forEach(({ image }) => expect(image.style.getPropertyValue('--nv-image-error-symbol')).toBe(''))
    expect(recovered.image.hasAttribute('aria-label')).toBe(false)
    expect(entries[1]!.image.hasAttribute('aria-label')).toBe(false)
    await Promise.resolve()
    entries.forEach(({ controller }) => controller.destroy())
    expect(frame.frames.size).toBe(0)
  })

  it('uses the latest label and fresh dimensions for repeated errors', () => {
    const frame = controlErrorFrames()
    const image = document.createElement('img')
    image.src = '/failed.png'
    image.title = 'Original title'
    let label = 'First failure'
    let width = 320
    Object.defineProperties(image, {
      complete: { value: true },
      naturalWidth: { configurable: true, value: 0 },
      clientWidth: { get: () => width },
      clientHeight: { get: () => 180 },
    })
    document.body.append(image)
    const controller = createImageStateController(image, () => label)
    cleanup.push(() => { controller.destroy(); image.remove() })
    label = 'Updated failure'
    controller.refresh()
    frame.flush()
    expect(decodeURIComponent(image.style.getPropertyValue('--nv-image-error-symbol'))).toContain('Updated failure')
    expect(image.title).toBe('Updated failure')

    Object.defineProperty(image, 'naturalWidth', { value: 320 })
    controller.refresh()
    expect(image.title).toBe('Original title')
    width = 32
    Object.defineProperty(image, 'naturalWidth', { value: 0 })
    controller.refresh()
    frame.flush()
    expect(image.style.getPropertyValue('--nv-image-error-symbol')).toBe('')
    expect(image.style.getPropertyValue('--nv-image-error-symbol-dark')).toBe('')
    expect(image.style.getPropertyValue('--nv-image-error-size')).toBe('')
    expect(image.getAttribute('aria-label')).toBe('Updated failure')
  })

  it('schedules and cancels using the image document window', () => {
    const parentFrame = controlErrorFrames()
    const iframe = document.createElement('iframe')
    document.body.append(iframe)
    const childWindow = iframe.contentWindow!
    const request = vi.fn(() => 42)
    const cancel = vi.fn()
    Object.defineProperties(childWindow, {
      requestAnimationFrame: { configurable: true, value: request },
      cancelAnimationFrame: { configurable: true, value: cancel },
    })
    const image = iframe.contentDocument!.createElement('img')
    image.src = '/missing.png'
    Object.defineProperties(image, { complete: { value: true }, naturalWidth: { value: 0 } })
    iframe.contentDocument!.body.append(image)
    const controller = createImageStateController(image, () => 'Failure')
    expect(request).toHaveBeenCalledOnce()
    expect(parentFrame.frames.size).toBe(0)
    controller.destroy()
    expect(cancel).toHaveBeenCalledExactlyOnceWith(42)
    iframe.remove()
  })
})


function mockVisibilityObserver() {
  const instances: VisibilityObserver[] = []
  class VisibilityObserver {
    observe = vi.fn()
    unobserve = vi.fn()
    disconnect = vi.fn()
    constructor(private callback: IntersectionObserverCallback) { instances.push(this) }
    emit(target: HTMLImageElement, isIntersecting: boolean) {
      const rect = target.getBoundingClientRect()
      this.callback([{
        target, isIntersecting, intersectionRatio: isIntersecting ? 1 : 0,
        boundingClientRect: rect, intersectionRect: rect, rootBounds: null, time: 0,
      }], this as unknown as IntersectionObserver)
    }
  }
  vi.stubGlobal('IntersectionObserver', VisibilityObserver)
  return { instances, VisibilityObserver }
}

describe('loading animation visibility', () => {
  it('shares an observer, resumes detached images when visible, and stops observing settled images', () => {
    const { instances } = mockVisibilityObserver()
    const image = document.createElement('img')
    image.src = '/pending.png'
    Object.defineProperties(image, {
      complete: { configurable: true, value: false },
      naturalWidth: { configurable: true, value: 0 },
    })
    const controller = createImageStateController(image, () => 'Failure')
    cleanup.push(() => { controller.destroy(); image.remove() })
    expect(image.hasAttribute('data-image-offscreen')).toBe(true)
    document.body.append(image)
    const second = setup('/other-pending.png')
    expect(instances).toHaveLength(1)
    const observer = instances[0]!
    expect(observer.observe).toHaveBeenCalledTimes(2)
    controller.refresh()
    expect(observer.observe).toHaveBeenCalledTimes(2)
    observer.emit(image, true)
    expect(image.hasAttribute('data-image-offscreen')).toBe(false)
    observer.emit(image, false)
    expect(image.hasAttribute('data-image-offscreen')).toBe(true)
    Object.defineProperty(image, 'complete', { value: true })
    Object.defineProperty(image, 'naturalWidth', { value: 320 })
    image.dispatchEvent(new Event('load'))
    expect(image.dataset.imageState).toBe('loaded')
    expect(observer.unobserve).toHaveBeenCalledWith(image)
    expect(image.hasAttribute('data-image-offscreen')).toBe(false)
    expect(observer.disconnect).not.toHaveBeenCalled()
    second.controller.destroy()
    expect(observer.disconnect).toHaveBeenCalledOnce()
    observer.emit(second.image, false)
    expect(second.image.hasAttribute('data-image-offscreen')).toBe(false)
  })

  it('cleans up failed images and observes a later loading source again', async () => {
    const { instances } = mockVisibilityObserver()
    const { image, controller } = setup('/first.png')
    image.dispatchEvent(new Event('error'))
    await Promise.resolve()
    expect(image.dataset.imageState).toBe('error')
    expect(instances[0]!.disconnect).toHaveBeenCalledOnce()
    expect(image.hasAttribute('data-image-offscreen')).toBe(false)
    image.src = '/replacement.png'
    await Promise.resolve()
    expect(image.dataset.imageState).toBe('loading')
    expect(instances).toHaveLength(2)
    expect(image.hasAttribute('data-image-offscreen')).toBe(true)
    controller.destroy()
    expect(instances[1]!.disconnect).toHaveBeenCalledOnce()
    expect(image.hasAttribute('data-image-offscreen')).toBe(false)
  })

  it('keeps visibility observations in each image owner document', () => {
    const { instances, VisibilityObserver } = mockVisibilityObserver()
    setup('/parent.png')
    const iframe = document.createElement('iframe')
    document.body.append(iframe)
    const childConstructor = vi.fn(function (callback: IntersectionObserverCallback) {
      return new VisibilityObserver(callback)
    })
    Object.defineProperty(iframe.contentWindow!, 'IntersectionObserver', { configurable: true, value: childConstructor })
    const image = iframe.contentDocument!.createElement('img')
    image.src = '/child.png'
    Object.defineProperty(image, 'complete', { value: false })
    iframe.contentDocument!.body.append(image)
    const controller = createImageStateController(image, () => 'Failure')
    expect(childConstructor).toHaveBeenCalledOnce()
    expect(instances).toHaveLength(2)
    expect(instances[1]!.observe).toHaveBeenCalledWith(image)
    controller.destroy()
    expect(instances[1]!.disconnect).toHaveBeenCalledOnce()
    expect(instances[0]!.disconnect).not.toHaveBeenCalled()
    iframe.remove()
  })

  it('leaves animation available when IntersectionObserver is unsupported', () => {
    vi.stubGlobal('IntersectionObserver', undefined)
    const { image } = setup()
    expect(image.dataset.imageState).toBe('loading')
    expect(image.hasAttribute('data-image-offscreen')).toBe(false)
  })
})

function mockErrorResizeObserver() {
  const instances: ErrorResizeObserver[] = []
  class ErrorResizeObserver {
    observe = vi.fn()
    unobserve = vi.fn()
    disconnect = vi.fn()
    constructor(private callback: ResizeObserverCallback) { instances.push(this) }
    emit(...targets: HTMLImageElement[]) {
      this.callback(targets.map((target): ResizeObserverEntry => ({
        target, contentRect: target.getBoundingClientRect(),
        borderBoxSize: [], contentBoxSize: [], devicePixelContentBoxSize: [],
      })), this as unknown as ResizeObserver)
    }
  }
  vi.stubGlobal('ResizeObserver', ErrorResizeObserver)
  return { instances, ErrorResizeObserver }
}

describe('error presentation resizing', () => {
  it('updates hidden, expanded and compact failures only when their size threshold changes', () => {
    const frame = controlErrorFrames()
    const { instances } = mockErrorResizeObserver()
    const { image } = setup('/missing.png', true)
    let width = 0
    let height = 0
    Object.defineProperties(image, {
      clientWidth: { get: () => width }, clientHeight: { get: () => height },
    })
    frame.flush()
    const writes = vi.spyOn(image.style, 'setProperty')
    const observer = instances[0]!
    expect(image.style.getPropertyValue('--nv-image-error-symbol')).toBe('')
    width = 400
    height = 200
    observer.emit(image)
    frame.flush()
    expect(decodeURIComponent(image.style.getPropertyValue('--nv-image-error-symbol'))).toContain('Unable to load image.')
    writes.mockClear()
    width = 300
    height = 100
    observer.emit(image)
    frame.flush()
    expect(writes).not.toHaveBeenCalled()
    width = 179
    observer.emit(image)
    frame.flush()
    expect(image.style.getPropertyValue('--nv-image-error-symbol')).toBe('')
    expect(image.style.getPropertyValue('--nv-image-error-symbol-dark')).toBe('')
    expect(image.style.getPropertyValue('--nv-image-error-size')).toBe('')
    width = 180
    height = 79
    observer.emit(image)
    frame.flush()
    expect(image.style.getPropertyValue('--nv-image-error-symbol')).toBe('')
    height = 80
    observer.emit(image)
    frame.flush()
    expect(image.style.getPropertyValue('--nv-image-error-symbol-dark')).not.toBe('')
  })

  it('shares resize observations, batches geometry and releases recovered or destroyed images', () => {
    const frame = controlErrorFrames()
    const { instances } = mockErrorResizeObserver()
    const entries = [setup('/first.png', true), setup('/second.png', true)]
    const operations: string[] = []
    let width = 0
    entries.forEach(({ image }) => {
      Object.defineProperties(image, {
        clientWidth: { get: () => { operations.push('read'); return width } },
        clientHeight: { get: () => { operations.push('read'); return 200 } },
      })
      const set = image.style.setProperty.bind(image.style)
      vi.spyOn(image.style, 'setProperty').mockImplementation((name, value, priority) => {
        if (name === '--nv-image-error-symbol') operations.push('write')
        set(name, value, priority)
      })
    })
    frame.flush()
    operations.length = 0
    width = 400
    const observer = instances[0]!
    expect(instances).toHaveLength(1)
    observer.emit(...entries.map(({ image }) => image))
    expect(frame.frames.size).toBe(1)
    expect(operations).toEqual([])
    frame.flush()
    expect(operations).toEqual(['read', 'read', 'read', 'read', 'write', 'write'])
    observer.emit(...entries.map(({ image }) => image))
    Object.defineProperty(entries[0]!.image, 'naturalWidth', { value: 400 })
    entries[0]!.image.dispatchEvent(new Event('load'))
    entries[1]!.controller.destroy()
    expect(observer.unobserve).toHaveBeenCalledTimes(2)
    expect(observer.disconnect).toHaveBeenCalledOnce()
    expect(frame.frames.size).toBe(0)
    observer.emit(...entries.map(({ image }) => image))
    expect(frame.frames.size).toBe(0)
  })

  it('uses the image owner document observer and ignores a queued resize after source replacement', () => {
    const parentFrame = controlErrorFrames()
    const { instances, ErrorResizeObserver } = mockErrorResizeObserver()
    const iframe = document.createElement('iframe')
    document.body.append(iframe)
    const frames: FrameRequestCallback[] = []
    Object.defineProperties(iframe.contentWindow!, {
      ResizeObserver: { configurable: true, value: ErrorResizeObserver },
      requestAnimationFrame: { configurable: true, value: (callback: FrameRequestCallback) => frames.push(callback) },
      cancelAnimationFrame: { configurable: true, value: vi.fn() },
    })
    const image = iframe.contentDocument!.createElement('img')
    image.src = '/missing.png'
    let width = 400
    Object.defineProperties(image, {
      complete: { value: true }, naturalWidth: { value: 0 },
      clientWidth: { get: () => width }, clientHeight: { value: 200 },
    })
    iframe.contentDocument!.body.append(image)
    const controller = createImageStateController(image, () => 'Failure')
    expect(parentFrame.frames.size).toBe(0)
    frames.shift()!(0)
    const writes = vi.spyOn(image.style, 'setProperty')
    const removals = vi.spyOn(image.style, 'removeProperty')
    width = 20
    instances[0]!.emit(image)
    image.src = '/replacement.png'
    frames.shift()!(0)
    expect(writes).not.toHaveBeenCalled()
    expect(removals).not.toHaveBeenCalled()
    controller.destroy()
    expect(instances[0]!.disconnect).toHaveBeenCalledOnce()
    iframe.remove()
  })
})

describe('error image accessibility', () => {
  it.each([
    { name: 'decorative', alt: '', ariaLabel: null, labelledBy: null, decorative: true },
    { name: 'alt description', alt: 'Read the guide', ariaLabel: null, labelledBy: null, decorative: false },
    { name: 'explicit label', alt: '', ariaLabel: 'Download report', labelledBy: null, decorative: false },
    { name: 'explicit label over alt', alt: 'Thumbnail', ariaLabel: 'Download report', labelledBy: null, decorative: false },
    { name: 'label reference', alt: '', ariaLabel: null, labelledBy: 'image-description', decorative: false },
    { name: 'label reference over label', alt: 'Thumbnail', ariaLabel: 'Fallback label', labelledBy: 'image-description', decorative: false },
  ])('preserves $name through errors, language changes and recovery', ({ alt, ariaLabel, labelledBy, decorative }) => {
    const image = document.createElement('img')
    image.src = '/missing.png'
    image.alt = alt
    image.title = 'Original tooltip'
    if (ariaLabel !== null) image.setAttribute('aria-label', ariaLabel)
    if (labelledBy !== null) image.setAttribute('aria-labelledby', labelledBy)
    Object.defineProperties(image, {
      complete: { value: true }, naturalWidth: { configurable: true, value: 0 },
    })
    document.body.append(image)
    let label = '이미지를 불러올 수 없습니다.'
    const controller = createImageStateController(image, () => label)
    cleanup.push(() => { controller.destroy(); image.remove() })
    for (const translation of ['이미지를 불러올 수 없습니다.', 'Unable to load image.']) {
      label = translation
      controller.refreshErrorLabel()
      expect(image.getAttribute('aria-label')).toBe(decorative || labelledBy ? ariaLabel : (ariaLabel || alt) + ' — ' + translation)
      expect(image.title).toBe(decorative ? 'Original tooltip' : translation)
      expect(image.getAttribute('aria-labelledby')).toBe(labelledBy)
    }
    Object.defineProperty(image, 'naturalWidth', { value: 400 })
    controller.refresh()
    expect(image.dataset.imageState).toBe('loaded')
    expect(image.getAttribute('aria-label')).toBe(ariaLabel)
    expect(image.title).toBe('Original tooltip')
    expect(image.getAttribute('aria-labelledby')).toBe(labelledBy)
  })

  it('does not introduce an accessible label or tooltip on an unlabeled decorative failure', () => {
    const image = document.createElement('img')
    image.alt = ''
    document.body.append(image)
    const controller = createImageStateController(image, () => 'Unable to load image.')
    cleanup.push(() => { controller.destroy(); image.remove() })
    expect(image.dataset.imageState).toBe('error')
    expect(image.hasAttribute('aria-label')).toBe(false)
    expect(image.hasAttribute('title')).toBe(false)
  })
})
