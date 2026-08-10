import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import SandboxedHtmlFrame from '../SandboxedHtmlFrame.vue'

function extractFrameId(srcdoc: string): string {
  const match = srcdoc.match(/var frameId = "([^"]+)";/)
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
