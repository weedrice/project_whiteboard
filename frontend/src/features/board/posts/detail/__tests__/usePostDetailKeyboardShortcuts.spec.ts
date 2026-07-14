import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePostDetailKeyboardShortcuts } from '../usePostDetailKeyboardShortcuts'

const mocks = vi.hoisted(() => ({
  inputFocused: false,
  listener: null as ((event: KeyboardEvent) => void) | null,
}))

vi.mock('@/composables/useEventListener', () => ({
  useEventListener: (_target: unknown, _event: string, listener: (event: KeyboardEvent) => void) => {
    mocks.listener = listener
  },
}))
vi.mock('@/utils/keyboard', () => ({ isInputFocused: () => mocks.inputFocused }))

function key(key: string, options: KeyboardEventInit = {}) {
  return new KeyboardEvent('keydown', { key, cancelable: true, ...options })
}

function setup() {
  const actions = {
    scrollToComments: vi.fn(),
    goToList: vi.fn(),
    handleBookmark: vi.fn(),
    handleShare: vi.fn(),
    handleCopyUrl: vi.fn(),
    handleLike: vi.fn(),
  }
  const router = { push: vi.fn() }
  const result = usePostDetailKeyboardShortcuts({
    router: router as never,
    authStore: { isAuthenticated: true },
    postView: computed(() => ({ postId: 1 }) as never),
    canEdit: computed(() => true),
    isReportModalOpen: ref(false),
    buildEditRoute: () => '/edit',
    ...actions,
  })
  return { ...actions, router, result }
}

describe('usePostDetailKeyboardShortcuts', () => {
  beforeEach(() => {
    mocks.inputFocused = false
    mocks.listener = null
  })

  it('registers the handler and maps normal shortcuts', () => {
    const setupResult = setup()
    expect(mocks.listener).toBe(setupResult.result.handleKeyDown)

    for (const [shortcut, action] of [
      ['c', setupResult.scrollToComments],
      ['u', setupResult.goToList],
      ['l', setupResult.handleLike],
      ['y', setupResult.handleCopyUrl],
      ['Escape', setupResult.goToList],
    ] as const) {
      const event = key(shortcut)
      setupResult.result.handleKeyDown(event)
      expect(event.defaultPrevented).toBe(true)
      expect(action).toHaveBeenCalled()
    }

    setupResult.result.handleKeyDown(key('e'))
    expect(setupResult.router.push).toHaveBeenCalledWith('/edit')
  })

  it('maps shifted bookmark and share shortcuts', () => {
    const result = setup()
    result.result.handleKeyDown(key('S', { shiftKey: true }))
    result.result.handleKeyDown(key('Y', { shiftKey: true }))
    result.result.handleKeyDown(key('X', { shiftKey: true }))
    expect(result.handleBookmark).toHaveBeenCalledOnce()
    expect(result.handleShare).toHaveBeenCalledOnce()
  })

  it('ignores modified and input-focused events', () => {
    const result = setup()
    result.result.handleKeyDown(key('c', { ctrlKey: true }))
    result.result.handleKeyDown(key('c', { altKey: true }))
    result.result.handleKeyDown(key('c', { metaKey: true }))
    mocks.inputFocused = true
    result.result.handleKeyDown(key('c'))
    expect(result.scrollToComments).not.toHaveBeenCalled()
  })

  it('ignores auth/edit shortcuts when preconditions are not met', () => {
    const actions = {
      scrollToComments: vi.fn(), goToList: vi.fn(), handleBookmark: vi.fn(),
      handleShare: vi.fn(), handleCopyUrl: vi.fn(), handleLike: vi.fn(),
    }
    const router = { push: vi.fn() }
    const modal = ref(true)
    const result = usePostDetailKeyboardShortcuts({
      router: router as never,
      authStore: { isAuthenticated: false },
      postView: computed(() => null),
      canEdit: computed(() => false),
      isReportModalOpen: modal,
      buildEditRoute: () => '/edit',
      ...actions,
    })
    result.handleKeyDown(key('c'))
    modal.value = false
    result.handleKeyDown(key('l'))
    result.handleKeyDown(key('S', { shiftKey: true }))
    result.handleKeyDown(key('e'))
    expect(actions.scrollToComments).not.toHaveBeenCalled()
    expect(actions.handleLike).not.toHaveBeenCalled()
    expect(actions.handleBookmark).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
  })
})
