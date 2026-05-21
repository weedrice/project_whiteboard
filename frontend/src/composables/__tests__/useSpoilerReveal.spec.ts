import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'

import { useSpoilerReveal } from '../useSpoilerReveal'

function mountSpoiler() {
  const state = {} as ReturnType<typeof useSpoilerReveal>
  const wrapper = mount(defineComponent({
    setup() {
      Object.assign(state, useSpoilerReveal())
      return () => null
    }
  }))
  return { wrapper, spoiler: state }
}

describe('useSpoilerReveal', () => {
  it('starts blurred for spoiler posts and reveals after the countdown', () => {
    vi.useFakeTimers()
    const { wrapper, spoiler } = mountSpoiler()

    spoiler.syncSpoilerState(true)

    expect(spoiler.isBlurred.value).toBe(true)
    expect(spoiler.timeLeft.value).toBe(5)

    vi.advanceTimersByTime(5000)

    expect(spoiler.isBlurred.value).toBe(false)
    expect(spoiler.timeLeft.value).toBe(0)
    wrapper.unmount()
    vi.useRealTimers()
  })

  it('clears the blur state immediately for non-spoiler posts', () => {
    vi.useFakeTimers()
    const { wrapper, spoiler } = mountSpoiler()

    spoiler.syncSpoilerState(true)
    spoiler.syncSpoilerState(false)
    vi.advanceTimersByTime(5000)

    expect(spoiler.isBlurred.value).toBe(false)
    expect(spoiler.timeLeft.value).toBe(5)
    wrapper.unmount()
    vi.useRealTimers()
  })
})
