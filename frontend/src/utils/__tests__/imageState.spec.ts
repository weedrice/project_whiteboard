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
