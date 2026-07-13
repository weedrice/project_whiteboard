import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import PullToRefresh from '../PullToRefresh.vue'
import { isStandaloneDisplayMode } from '@/pwaDisplayMode'

function pointerEvent(
  type: string,
  { pointerId = 1, clientX = 0, clientY = 0, pointerType = 'touch' } = {},
) {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperties(event, {
    pointerId: { value: pointerId },
    clientX: { value: clientX },
    clientY: { value: clientY },
    pointerType: { value: pointerType },
  })
  return event
}

const standaloneMatchMedia = () => ({
  matches: true,
  media: '(display-mode: standalone)',
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
})

const mountPull = (refresh = vi.fn()) => mount(PullToRefresh, {
  props: { refresh },
  slots: { default: '<div data-testid="content"><input data-testid="input"></div>' },
  global: {
    mocks: { $t: (key: string) => key },
    stubs: {
      Check: true,
      LoaderCircle: true,
      RotateCw: true,
    },
  },
})

describe('PullToRefresh', () => {
  beforeEach(() => {
    vi.stubGlobal('matchMedia', standaloneMatchMedia)
    vi.spyOn(window, 'scrollY', 'get').mockReturnValue(0)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('detects browser and iOS standalone modes', () => {
    expect(isStandaloneDisplayMode(
      { matchMedia: () => ({ matches: true }) as MediaQueryList },
      {} as Navigator,
    )).toBe(true)
    expect(isStandaloneDisplayMode(
      { matchMedia: () => ({ matches: false }) as MediaQueryList },
      { standalone: true } as Navigator & { standalone: boolean },
    )).toBe(true)
  })

  it('refreshes after a vertical touch pull reaches the 72px threshold', async () => {
    const refresh = vi.fn().mockResolvedValue(undefined)
    const wrapper = mountPull(refresh)
    const root = wrapper.get('.nv-pull-to-refresh').element

    root.dispatchEvent(pointerEvent('pointerdown'))
    root.dispatchEvent(pointerEvent('pointermove', { clientY: 80 }))
    root.dispatchEvent(pointerEvent('pointerup', { clientY: 80 }))
    await nextTick()

    expect(refresh).toHaveBeenCalledOnce()
  })

  it('ignores excluded controls, horizontal gestures, and a second pointer', async () => {
    const refresh = vi.fn()
    const wrapper = mountPull(refresh)
    const root = wrapper.get('.nv-pull-to-refresh').element
    const input = wrapper.get('[data-testid="input"]').element

    input.dispatchEvent(pointerEvent('pointerdown'))
    root.dispatchEvent(pointerEvent('pointermove', { clientY: 100 }))
    root.dispatchEvent(pointerEvent('pointerup', { clientY: 100 }))

    root.dispatchEvent(pointerEvent('pointerdown'))
    root.dispatchEvent(pointerEvent('pointermove', { clientX: 90, clientY: 40 }))
    root.dispatchEvent(pointerEvent('pointerup', { clientX: 90, clientY: 40 }))

    root.dispatchEvent(pointerEvent('pointerdown'))
    root.dispatchEvent(pointerEvent('pointerdown', { pointerId: 2 }))
    root.dispatchEvent(pointerEvent('pointermove', { clientY: 100 }))
    root.dispatchEvent(pointerEvent('pointerup', { clientY: 100 }))
    await nextTick()

    expect(refresh).not.toHaveBeenCalled()
  })

  it('prevents duplicate refreshes while one is pending', async () => {
    const refresh = vi.fn().mockReturnValue(new Promise(() => undefined))
    const wrapper = mountPull(refresh)
    const root = wrapper.get('.nv-pull-to-refresh').element

    root.dispatchEvent(pointerEvent('pointerdown'))
    root.dispatchEvent(pointerEvent('pointermove', { clientY: 100 }))
    root.dispatchEvent(pointerEvent('pointerup', { clientY: 100 }))
    root.dispatchEvent(pointerEvent('pointerdown', { pointerId: 2 }))
    root.dispatchEvent(pointerEvent('pointermove', { pointerId: 2, clientY: 100 }))
    root.dispatchEvent(pointerEvent('pointerup', { pointerId: 2, clientY: 100 }))
    await nextTick()

    expect(refresh).toHaveBeenCalledOnce()
  })
})
