import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import SandboxedHtmlFrame from '../SandboxedHtmlFrame.vue'

function extractFrameId(srcdoc: string): string {
  const match = srcdoc.match(/data-frame-id="([^"]+)"/)
  if (!match) {
    throw new Error('frame id not found')
  }
  return match[1]
}

function postHeightMessage(frame: HTMLIFrameElement, id: string, height: unknown) {
  window.dispatchEvent(new MessageEvent('message', {
    data: {
      type: 'noviis-post-html-height',
      channel: 'noviis-post-html-sandbox',
      id,
      height,
    },
    source: frame.contentWindow,
  }))
}

describe('SandboxedHtmlFrame', () => {
  it('accepts height messages only from the managed sandbox frame', async () => {
    const wrapper = mount(SandboxedHtmlFrame, {
      props: {
        html: '<p>Hello</p>',
        minHeight: 240,
      },
    })
    const frame = wrapper.get('iframe').element as HTMLIFrameElement
    const frameId = extractFrameId(wrapper.get('iframe').attributes('srcdoc') ?? '')

    window.dispatchEvent(new MessageEvent('message', {
      data: {
        type: 'noviis-post-html-height',
        channel: 'noviis-post-html-sandbox',
        id: frameId,
        height: 1000,
      },
      source: window,
    }))
    await nextTick()

    expect(frame.style.height).toBe('240px')

    postHeightMessage(frame, frameId, 1000)
    await nextTick()

    expect(frame.style.height).toBe('1000px')
  })

  it('ignores malformed messages and clamps accepted heights', async () => {
    const wrapper = mount(SandboxedHtmlFrame, {
      props: {
        html: '<p>Hello</p>',
        minHeight: 240,
      },
    })
    const frame = wrapper.get('iframe').element as HTMLIFrameElement
    const frameId = extractFrameId(wrapper.get('iframe').attributes('srcdoc') ?? '')

    window.dispatchEvent(new MessageEvent('message', {
      data: {
        type: 'noviis-post-html-height',
        id: frameId,
        height: 1000,
      },
      source: frame.contentWindow,
    }))
    await nextTick()

    expect(frame.style.height).toBe('240px')

    postHeightMessage(frame, 'other-frame', 1000)
    await nextTick()

    expect(frame.style.height).toBe('240px')

    postHeightMessage(frame, frameId, 10)
    await nextTick()

    expect(frame.style.height).toBe('240px')

    postHeightMessage(frame, frameId, 10_000)
    await nextTick()

    expect(frame.style.height).toBe('4000px')
  })

  it('resets stale measured height when html changes', async () => {
    const wrapper = mount(SandboxedHtmlFrame, {
      props: {
        html: '<p>Long content</p>',
        minHeight: 240,
      },
    })
    const frame = wrapper.get('iframe').element as HTMLIFrameElement
    const frameId = extractFrameId(wrapper.get('iframe').attributes('srcdoc') ?? '')

    postHeightMessage(frame, frameId, 2000)
    await nextTick()
    expect(frame.style.height).toBe('2000px')

    await wrapper.setProps({ html: '<p>Short content</p>' })

    expect(frame.style.height).toBe('240px')
  })

  it('accepts a smaller measured height after the same document shrinks', async () => {
    const wrapper = mount(SandboxedHtmlFrame, {
      props: {
        html: '<p>Resizable content</p>',
        minHeight: 240,
      },
    })
    const frame = wrapper.get('iframe').element as HTMLIFrameElement
    const frameId = extractFrameId(wrapper.get('iframe').attributes('srcdoc') ?? '')

    postHeightMessage(frame, frameId, 1800)
    await nextTick()
    expect(frame.style.height).toBe('1800px')

    postHeightMessage(frame, frameId, 360)
    await nextTick()
    expect(frame.style.height).toBe('360px')
  })
})


describe('sandbox bridge failure recovery', () => {
  const wrappers: ReturnType<typeof mount>[] = []
  function setup() {
    vi.useFakeTimers()
    const wrapper = mount(SandboxedHtmlFrame, { props: { html: '<img src="/photo.png" alt="Photo">' } })
    wrappers.push(wrapper)
    return wrapper
  }
  function ready(frame: HTMLIFrameElement, id: string) {
    window.dispatchEvent(new MessageEvent('message', {
      source: frame.contentWindow,
      data: { type: 'noviis-post-html-ready', channel: 'noviis-post-html-sandbox', id },
    }))
  }
  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('restores native images once when the bridge never initializes', async () => {
    const wrapper = setup()
    await wrapper.get('iframe').trigger('load')
    await vi.advanceTimersByTimeAsync(1000)
    const source = wrapper.get('iframe').attributes('srcdoc')!
    expect(source).toContain('src="/photo.png"')
    expect(source).not.toContain('data-image-state=')
    expect(source).not.toContain('aria-busy=')
    expect(source).not.toContain('<script')
    await wrapper.get('iframe').trigger('load')
    await vi.advanceTimersByTimeAsync(20_000)
    expect(wrapper.get('iframe').attributes('srcdoc')).toBe(source)
  })

  it('accepts readiness without a height measurement and preserves it across minHeight updates', async () => {
    const wrapper = setup()
    const source = wrapper.get('iframe').attributes('srcdoc')!
    await wrapper.get('iframe').trigger('load')
    ready(wrapper.get('iframe').element as HTMLIFrameElement, extractFrameId(source))
    await wrapper.setProps({ minHeight: 300 })
    await vi.advanceTimersByTimeAsync(2000)
    expect(wrapper.get('iframe').attributes('srcdoc')).toBe(source)
  })

  it('rejects a previous document readiness message after HTML changes', async () => {
    const wrapper = setup()
    const previousId = extractFrameId(wrapper.get('iframe').attributes('srcdoc')!)
    await wrapper.get('iframe').trigger('load')
    await wrapper.setProps({ html: '<img src="/new.png">' })
    const nextId = extractFrameId(wrapper.get('iframe').attributes('srcdoc')!)
    expect(nextId).not.toBe(previousId)
    ready(wrapper.get('iframe').element as HTMLIFrameElement, previousId)
    await wrapper.get('iframe').trigger('load')
    await vi.advanceTimersByTimeAsync(1000)
    expect(wrapper.get('iframe').attributes('srcdoc')).not.toContain('data-image-state=')
  })

  it('retries managed rendering for new content after a static recovery', async () => {
    const wrapper = setup()
    await wrapper.get('iframe').trigger('load')
    await vi.advanceTimersByTimeAsync(1000)
    await wrapper.setProps({ html: '<img src="/replacement.png">' })
    const source = wrapper.get('iframe').attributes('srcdoc')!
    expect(source).toContain('data-image-state="loading"')
    ready(wrapper.get('iframe').element as HTMLIFrameElement, extractFrameId(source))
    await wrapper.get('iframe').trigger('load')
    await vi.advanceTimersByTimeAsync(2000)
    expect(wrapper.get('iframe').attributes('srcdoc')).toBe(source)
  })

  it('starts the stalled-load watchdog only when the lazy frame becomes visible', async () => {
    let notifyVisibility!: (entries: Array<{ isIntersecting: boolean }>) => void
    const disconnect = vi.fn()
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: typeof notifyVisibility) { notifyVisibility = callback }
      observe() {}
      disconnect = disconnect
    })
    const wrapper = setup()
    const source = wrapper.get('iframe').attributes('srcdoc')!
    notifyVisibility([{ isIntersecting: false }])
    await vi.advanceTimersByTimeAsync(20_000)
    expect(wrapper.get('iframe').attributes('srcdoc')).toBe(source)
    notifyVisibility([{ isIntersecting: true }])
    await vi.advanceTimersByTimeAsync(10_000)
    expect(wrapper.get('iframe').attributes('srcdoc')).not.toContain('data-image-state=')
    wrappers.pop()!.unmount()
    expect(disconnect).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('cancels recovery when the frame is unmounted', async () => {
    const wrapper = setup()
    await wrapper.get('iframe').trigger('load')
    expect(vi.getTimerCount()).toBeGreaterThan(0)
    wrappers.pop()!.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
})
