import { getCurrentQuerySessionSource } from '@/queryAuthScope'
import { createAuthSessionScope } from '@/utils/authSessionScope'

export const AUTHENTICATED_IMAGE_CHANGE_EVENT = 'authenticatedimagechange'
export const AUTHENTICATED_IMAGES_RESET_EVENT = 'authenticatedimagesreset'
const IMAGE_SOURCE_ATTRIBUTE = 'data-authenticated-file-src'
const MAX_CONCURRENT_DOWNLOADS = 4
const VIEWPORT_MARGIN = 600

export interface AuthenticatedImageResource {
  readonly id: string
  readonly src: string
  readonly pending: boolean
  readonly failed: boolean
  requestLoad: (retry?: boolean) => void
}

const resources = new WeakMap<HTMLImageElement, AuthenticatedImageResource>()
let nextImageId = 0

export function getAuthenticatedImageResource(image: HTMLImageElement) {
  return resources.get(image)
}

/** One content owner shares downloads by path and owns their session and blobs. */
export function createAuthenticatedImageLoader(
  root: HTMLElement,
  onState: (image: HTMLImageElement, state: 'loading' | 'loaded' | 'error') => void,
) {
  type Job = {
    path: string
    nodes: HTMLImageElement[]
    state: 'idle' | 'queued' | 'loading' | 'loaded' | 'error'
    src: string
  }
  const scope = createAuthSessionScope(getCurrentQuerySessionSource())
  const jobs = new Map<string, Job>()
  const queue: Job[] = []
  let active = 0
  let disposed = false
  let observer: IntersectionObserver | null = null
  let apiPromise: Promise<typeof import('@/api')> | null = null

  function publish(job: Job, state: 'loading' | 'loaded' | 'error') {
    for (const image of job.nodes) {
      if (!root.contains(image)) continue
      if (job.src) image.src = job.src
      else image.removeAttribute('src')
      onState(image, state)
      image.dispatchEvent(new Event(AUTHENTICATED_IMAGE_CHANGE_EVENT, { bubbles: true }))
    }
  }

  async function download(job: Job) {
    try {
      const { default: api } = await (apiPromise ??= import('@/api'))
      if (disposed || !scope.isCurrent()) return
      const response = await api.get<Blob>(job.path, scope.withRequestConfig({
        responseType: 'blob',
        skipGlobalErrorHandler: true,
      }))
      if (disposed || !scope.isCurrent() || !job.nodes.some(image => root.contains(image))) return
      job.src = URL.createObjectURL(response.data)
      job.state = 'loaded'
      publish(job, 'loaded')
    } catch {
      if (disposed || !scope.isCurrent()) return
      job.state = 'error'
      publish(job, 'error')
    } finally {
      active -= 1
      pump()
    }
  }

  function pump() {
    if (disposed || !scope.isCurrent()) return
    while (active < MAX_CONCURRENT_DOWNLOADS && queue.length > 0) {
      const job = queue.shift()!
      if (!job.nodes.some(image => root.contains(image))) continue
      job.state = 'loading'
      active += 1
      void download(job)
    }
  }

  function enqueue(job: Job, priority: boolean, retry = false) {
    if (disposed || !scope.isCurrent() || job.state === 'loading') return
    if (job.state === 'queued') {
      if (priority) {
        queue.splice(queue.indexOf(job), 1)
        queue.unshift(job)
      }
      return
    }
    if (!retry && (job.state === 'loaded' || job.state === 'error')) return
    if (job.src) URL.revokeObjectURL(job.src)
    job.src = ''
    job.state = 'queued'
    job.nodes.forEach(image => observer?.unobserve(image))
    publish(job, 'loading')
    if (priority) queue.unshift(job)
    else queue.push(job)
    pump()
  }

  root.querySelectorAll<HTMLImageElement>(`img[${IMAGE_SOURCE_ATTRIBUTE}]`).forEach(image => {
    const path = image.getAttribute(IMAGE_SOURCE_ATTRIBUTE)
    if (!path) return
    let job = jobs.get(path)
    if (!job) {
      job = { path, nodes: [], state: 'idle', src: '' }
      jobs.set(path, job)
    }
    job.nodes.push(image)
    const shared = job
    resources.set(image, {
      id: `authenticated-image-${++nextImageId}`,
      get src() { return shared.src },
      get pending() { return !['loaded', 'error'].includes(shared.state) },
      get failed() { return shared.state === 'error' },
      requestLoad: (retry = false) => enqueue(shared, true, retry),
    })
  })

  const Observer = root.ownerDocument.defaultView?.IntersectionObserver
  if (Observer) {
    observer = new Observer(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue
        const path = (entry.target as HTMLImageElement).getAttribute(IMAGE_SOURCE_ATTRIBUTE)
        const job = path ? jobs.get(path) : undefined
        if (job) enqueue(job, false)
      }
    }, { rootMargin: `${VIEWPORT_MARGIN}px` })
  }
  for (const job of jobs.values()) {
    for (const image of job.nodes) {
      observer?.observe(image)
      const bounds = image.getBoundingClientRect()
      const view = image.ownerDocument.defaultView
      const nearViewport = bounds.top <= (view?.innerHeight ?? 0) + VIEWPORT_MARGIN
        && bounds.bottom >= -VIEWPORT_MARGIN
      if (!observer || image.getAttribute('loading') === 'eager' || nearViewport) enqueue(job, false)
    }
  }

  return {
    destroy() {
      if (disposed) return
      disposed = true
      scope.cancel()
      scope.dispose()
      observer?.disconnect()
      queue.length = 0
      // Close consumers before revoking URLs they may currently display.
      root.dispatchEvent(new Event(AUTHENTICATED_IMAGES_RESET_EVENT, { bubbles: true }))
      for (const job of jobs.values()) {
        for (const image of job.nodes) resources.delete(image)
        if (job.src) URL.revokeObjectURL(job.src)
      }
      jobs.clear()
    },
  }
}
