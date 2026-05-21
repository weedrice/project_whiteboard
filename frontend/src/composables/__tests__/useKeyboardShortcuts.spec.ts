import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardDetailShortcuts } from '../useKeyboardShortcuts'

const authState = vi.hoisted(() => ({
  isAuthenticated: true,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('@/utils/keyboard', () => ({
  isInputFocused: () => false,
}))

function mountBoardShortcuts(overrides: Partial<Parameters<typeof useBoardDetailShortcuts>[0]> = {}) {
  const handlers = {
    goToNextPage: vi.fn(),
    goToPrevPage: vi.fn(),
    goToFirstPage: vi.fn(),
    goToLastPage: vi.fn(),
    goToWrite: vi.fn(),
    toggleSubscribe: vi.fn(),
    focusSearch: vi.fn(),
    canWrite: () => true,
    canGoNext: () => true,
    canGoPrev: () => true,
    ...overrides,
  }

  const Host = defineComponent({
    setup() {
      useBoardDetailShortcuts(handlers)
      return () => null
    },
  })

  return {
    handlers,
    wrapper: mount(Host),
  }
}

describe('useBoardDetailShortcuts', () => {
  beforeEach(() => {
    authState.isAuthenticated = true
  })

  it('routes board navigation keys through function guards', async () => {
    const { handlers, wrapper } = mountBoardShortcuts()
    await nextTick()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: ']' }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: '[' }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: '[', shiftKey: true }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: ']', shiftKey: true }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'n' }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: '/' }))

    expect(handlers.goToNextPage).toHaveBeenCalledTimes(1)
    expect(handlers.goToPrevPage).toHaveBeenCalledTimes(1)
    expect(handlers.goToFirstPage).toHaveBeenCalledTimes(1)
    expect(handlers.goToLastPage).toHaveBeenCalledTimes(1)
    expect(handlers.goToWrite).toHaveBeenCalledTimes(1)
    expect(handlers.focusSearch).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('honors subscribe and ignore guards', async () => {
    const { handlers, wrapper } = mountBoardShortcuts({
      canToggleSubscribe: () => false,
      shouldIgnoreShortcut: () => false,
    })
    await nextTick()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'f' }))
    expect(handlers.toggleSubscribe).not.toHaveBeenCalled()

    wrapper.unmount()

    const ignored = mountBoardShortcuts({
      shouldIgnoreShortcut: () => true,
    })
    await nextTick()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: ']' }))
    expect(ignored.handlers.goToNextPage).not.toHaveBeenCalled()

    ignored.wrapper.unmount()
  })
})
