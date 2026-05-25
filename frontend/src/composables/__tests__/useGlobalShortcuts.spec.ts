import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useGlobalShortcuts } from '../useGlobalShortcuts'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}))

const TestHost = defineComponent({
  setup() {
    const { registerShortcut } = useGlobalShortcuts()
    registerShortcut({
      key: '/',
      handler: vi.fn(),
    })

    return () => null
  },
})

type ListenerCall = [string, EventListenerOrEventListenerObject, ...unknown[]]

describe('useGlobalShortcuts', () => {
  let addEventListenerSpy: ReturnType<typeof vi.spyOn>
  let removeEventListenerSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    vi.useFakeTimers()
    pushMock.mockClear()
    addEventListenerSpy = vi.spyOn(document, 'addEventListener')
    removeEventListenerSpy = vi.spyOn(document, 'removeEventListener')
  })

  afterEach(() => {
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('removes global keydown listeners and clears the g-key timer on unmount', async () => {
    const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout')
    const wrapper = mount(TestHost)

    await nextTick()

    const keydownHandlers = (addEventListenerSpy.mock.calls as ListenerCall[])
      .filter((call) => call[0] === 'keydown')
      .map((call) => call[1])

    expect(keydownHandlers).toHaveLength(2)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'g' }))
    wrapper.unmount()

    const removedKeydownHandlers = (removeEventListenerSpy.mock.calls as ListenerCall[])
      .filter((call) => call[0] === 'keydown')
      .map((call) => call[1])

    expect(removedKeydownHandlers).toEqual(expect.arrayContaining(keydownHandlers))
    expect(clearTimeoutSpy).toHaveBeenCalled()
  })
})
