import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { createAuthenticatedImageLoader, getAuthenticatedImageResource } from '@/utils/authenticatedImageLoader'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { createDeferred } from '@/test/async'

const get = vi.hoisted(() => vi.fn())
vi.mock('@/api', () => ({ default: { get } }))
const cleanups: Array<() => void> = []

beforeEach(() => {
  get.mockReset()
  configureAuthQueryScope(() => 0)
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:protected')
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
})
afterEach(() => {
  cleanups.splice(0).reverse().forEach(cleanup => cleanup())
  configureAuthQueryScope(() => 0)
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function setup(paths: string[], visibleCount = paths.length, configure?: (images: HTMLImageElement[]) => void) {
  const root = document.createElement('div')
  root.innerHTML = paths.map(path => `<img data-authenticated-file-src="${path}">`).join('')
  const images = Array.from(root.querySelectorAll('img'))
  images.forEach((image, index) => {
    vi.spyOn(image, 'getBoundingClientRect').mockReturnValue({
      top: index < visibleCount ? 0 : 12000, bottom: index < visibleCount ? 200 : 12200,
    } as DOMRect)
  })
  configure?.(images)
  const state = vi.fn()
  const loader = createAuthenticatedImageLoader(root, state)
  cleanups.push(loader.destroy)
  return { root, images, state, loader }
}

describe('authenticated image downloads', () => {
  it('defers offscreen images when the browser defaults the loading property to eager', async () => {
    get.mockResolvedValue({ data: new Blob(['image']) })
    const { images } = setup(['/files/1'], 0, images => {
      Object.defineProperty(images[0]!, 'loading', { value: 'eager' })
    })
    expect(images[0]!.loading).toBe('eager')
    expect(images[0]!.getAttribute('loading')).toBeNull()
    await flushPromises()
    expect(get).not.toHaveBeenCalled()
  })

  it('honors explicitly eager offscreen images', async () => {
    get.mockResolvedValue({ data: new Blob(['image']) })
    setup(['/files/1'], 0, images => { images[0]!.setAttribute('loading', 'eager') })
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('starts a deferred image when it enters the observer margin and stops observing on disposal', async () => {
    let notify!: IntersectionObserverCallback
    const disconnect = vi.fn()
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: IntersectionObserverCallback) { notify = callback }
      observe = vi.fn()
      unobserve = vi.fn()
      disconnect = disconnect
    })
    get.mockResolvedValue({ data: new Blob(['image']) })
    const { images, loader } = setup(['/files/1'], 0)
    await flushPromises()
    expect(get).not.toHaveBeenCalled()
    notify([{
      target: images[0]!,
      isIntersecting: true,
      intersectionRatio: 1,
      time: 0,
      boundingClientRect: images[0]!.getBoundingClientRect(),
      intersectionRect: images[0]!.getBoundingClientRect(),
      rootBounds: null,
    }], {} as IntersectionObserver)
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(1)
    loader.destroy()
    expect(disconnect).toHaveBeenCalledTimes(1)
  })

  it('loads visible images only and shares a download when an offscreen occurrence is selected', async () => {
    get.mockResolvedValue({ data: new Blob(['image']) })
    const { images } = setup(['/files/1', '/files/2', '/files/2'], 1)
    await flushPromises()
    expect(get.mock.calls.map(([path]) => path)).toEqual(['/files/1'])
    getAuthenticatedImageResource(images[1]!)!.requestLoad()
    await flushPromises()
    expect(get.mock.calls.map(([path]) => path)).toEqual(['/files/1', '/files/2'])
    expect(images[1]!.src).toBe(images[2]!.src)
    expect(getAuthenticatedImageResource(images[1]!)!.id).not.toBe(getAuthenticatedImageResource(images[2]!)!.id)
  })

  it('limits concurrent downloads to four and prioritizes a gallery selection over queued images', async () => {
    const pending = Array.from({ length: 7 }, () => createDeferred<{ data: Blob }>())
    get.mockImplementation((path: string) => pending[Number(path.split('/').at(-1)) - 1]!.promise)
    const { images, loader } = setup(Array.from({ length: 7 }, (_, index) => `/files/${index + 1}`))
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(4)
    getAuthenticatedImageResource(images[6]!)!.requestLoad()
    pending[0]!.resolve({ data: new Blob(['image']) })
    await flushPromises()
    expect(get.mock.calls.map(([path]) => path)).toEqual(['/files/1', '/files/2', '/files/3', '/files/4', '/files/7'])
    loader.destroy()
    pending.forEach(response => response.resolve({ data: new Blob(['late']) }))
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(5)
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
  })

  it('captures the queue owner before dispatch and discards delayed results after a session change', async () => {
    let generation = 1
    let userId = 10
    configureAuthQueryScope(() => generation, () => userId)
    const pending = createDeferred<{ data: Blob }>()
    get.mockReturnValue(pending.promise)
    const { images } = setup(Array.from({ length: 6 }, (_, index) => `/files/${index + 1}`))
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(4)
    expect(get.mock.calls[0]![1].expectedSession).toEqual({ sessionGeneration: 1, userId: 10 })
    generation = 2
    userId = 20
    notifyAuthSessionBoundary(generation)
    expect(get.mock.calls[0]![1].signal.aborted).toBe(true)
    getAuthenticatedImageResource(images[5]!)!.requestLoad()
    pending.resolve({ data: new Blob(['old']) })
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(4)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('does not dispatch a deferred import with another account even before a boundary notification', async () => {
    let userId = 10
    configureAuthQueryScope(() => 1, () => userId)
    setup(['/files/1'])
    userId = 20
    await flushPromises()
    expect(get).not.toHaveBeenCalled()
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('retries failed downloads and decoded blobs while releasing owned URLs on retry and disposal', async () => {
    get.mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ data: new Blob(['image']) })
    const { images, loader } = setup(['/files/1'])
    const resource = getAuthenticatedImageResource(images[0]!)!
    await flushPromises()
    expect(resource.failed).toBe(true)
    resource.requestLoad(true)
    resource.requestLoad(true)
    expect(resource.pending).toBe(true)
    expect(resource.failed).toBe(false)
    await flushPromises()
    expect(get).toHaveBeenCalledTimes(2)
    expect(resource.pending).toBe(false)
    resource.requestLoad(true)
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1)
    await flushPromises()
    loader.destroy()
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
    expect(getAuthenticatedImageResource(images[0]!)).toBeUndefined()
    resource.requestLoad(true)
    expect(get).toHaveBeenCalledTimes(3)
  })
})
