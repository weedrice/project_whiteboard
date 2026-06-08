import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { useMediaQuery, useMobileViewport } from '../useMediaQuery'

function mountComposable(setup: () => void) {
  return mount(defineComponent({
    setup,
    template: '<div />',
  }))
}

describe('useMediaQuery', () => {
  const originalMatchMedia = window.matchMedia
  const originalInnerWidth = window.innerWidth
  let changeHandler: ((event: MediaQueryListEvent) => void) | null = null
  let removeEventListener: ReturnType<typeof vi.fn>

  beforeEach(() => {
    changeHandler = null
    removeEventListener = vi.fn()
    window.matchMedia = vi.fn((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn((_event: string, handler: (event: MediaQueryListEvent) => void) => {
        changeHandler = handler
      }),
      removeEventListener,
      dispatchEvent: vi.fn(),
    }) as unknown as MediaQueryList)
  })

  afterEach(() => {
    window.matchMedia = originalMatchMedia
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalInnerWidth })
  })

  it('uses the current media query match as the initial value', () => {
    window.matchMedia = vi.fn(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }) as unknown as MediaQueryList)
    let matches = false

    const wrapper = mountComposable(() => {
      matches = useMediaQuery('(max-width: 639px)').value
    })

    expect(matches).toBe(true)
    wrapper.unmount()
  })

  it('updates matches from change events and removes the listener on unmount', () => {
    let matches = false
    const onChange = vi.fn()

    const wrapper = mountComposable(() => {
      const state = useMediaQuery('(max-width: 639px)', false, onChange)
      matches = state.value
    })

    expect(matches).toBe(false)
    expect(onChange).toHaveBeenCalledWith(false)

    changeHandler?.({ matches: true } as MediaQueryListEvent)

    expect(onChange).toHaveBeenCalledWith(true)
    wrapper.unmount()
    expect(removeEventListener).toHaveBeenCalledWith('change', expect.any(Function))
  })

  it('falls back to the current window width when matchMedia is unavailable', () => {
    window.matchMedia = undefined as unknown as typeof window.matchMedia
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 500 })
    let matches = false

    const wrapper = mountComposable(() => {
      matches = useMobileViewport().value
    })

    expect(matches).toBe(true)
    wrapper.unmount()
  })
})
