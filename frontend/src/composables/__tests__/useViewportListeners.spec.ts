import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useViewportListeners } from '../useViewportListeners'

const mountedWrappers: VueWrapper[] = []

describe('useViewportListeners', () => {
  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    vi.restoreAllMocks()
  })

  it('starts and stops viewport listeners manually', () => {
    const onViewportChange = vi.fn()
    const onStop = vi.fn()
    let controls!: ReturnType<typeof useViewportListeners>

    const Harness = defineComponent({
      setup() {
        controls = useViewportListeners(onViewportChange, { onStop })
        return () => h('div')
      },
    })

    mountedWrappers.push(mount(Harness))
    window.dispatchEvent(new Event('resize'))
    expect(onViewportChange).not.toHaveBeenCalled()

    controls.startListening()
    window.dispatchEvent(new Event('resize'))
    window.dispatchEvent(new Event('scroll'))
    expect(onViewportChange).toHaveBeenCalledTimes(2)

    controls.stopListening()
    window.dispatchEvent(new Event('resize'))
    expect(onViewportChange).toHaveBeenCalledTimes(2)
    expect(onStop).toHaveBeenCalledTimes(1)
  })
})
