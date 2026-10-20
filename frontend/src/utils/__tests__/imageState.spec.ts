import { afterEach, describe, expect, it } from 'vitest'
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

afterEach(() => cleanup.splice(0).forEach((dispose) => dispose()))

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
