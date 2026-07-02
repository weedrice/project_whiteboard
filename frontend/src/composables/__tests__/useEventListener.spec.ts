import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useEventListener } from '../useEventListener'

const mountedWrappers: VueWrapper[] = []

const mountHarness = (listener = vi.fn()) => {
  let controls!: ReturnType<typeof useEventListener>

  const Harness = defineComponent({
    setup() {
      controls = useEventListener(() => document, 'keydown', listener)
      return () => h('div')
    },
  })

  const wrapper = mount(Harness)
  mountedWrappers.push(wrapper)

  return {
    controls,
    listener,
    wrapper,
  }
}

const dispatchKey = () => {
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'A', bubbles: true }))
}

describe('useEventListener', () => {
  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => {
      wrapper.unmount()
    })
    vi.restoreAllMocks()
  })

  it('registers a listener on mount and removes it on unmount', () => {
    const { listener, wrapper } = mountHarness()

    dispatchKey()
    wrapper.unmount()
    dispatchKey()

    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('can stop and restart the listener manually', () => {
    const { controls, listener } = mountHarness()

    controls.stop()
    dispatchKey()
    controls.start()
    dispatchKey()

    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('can defer initial registration until start is called', () => {
    const listener = vi.fn()
    let controls!: ReturnType<typeof useEventListener>
    const Harness = defineComponent({
      setup() {
        controls = useEventListener(() => document, 'keydown', listener, undefined, { autoStart: false })
        return () => h('div')
      },
    })

    mountedWrappers.push(mount(Harness))
    dispatchKey()
    controls.start()
    dispatchKey()

    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('does nothing when the target is unavailable', () => {
    const listener = vi.fn()
    const Harness = defineComponent({
      setup() {
        useEventListener(() => null, 'keydown', listener)
        return () => h('div')
      },
    })

    mountedWrappers.push(mount(Harness))
    dispatchKey()

    expect(listener).not.toHaveBeenCalled()
  })

  it('rebinds the listener when a reactive target changes', async () => {
    const listener = vi.fn()
    const firstTarget = new EventTarget()
    const secondTarget = new EventTarget()
    const target = ref<EventTarget | null>(firstTarget)
    const Harness = defineComponent({
      setup() {
        useEventListener(target, 'change', listener)
        return () => h('div')
      },
    })

    mountedWrappers.push(mount(Harness))
    firstTarget.dispatchEvent(new Event('change'))

    target.value = secondTarget
    await nextTick()
    firstTarget.dispatchEvent(new Event('change'))
    secondTarget.dispatchEvent(new Event('change'))

    expect(listener).toHaveBeenCalledTimes(2)
  })
})
