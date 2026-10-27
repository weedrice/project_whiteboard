import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import { useEventListener } from '@/composables/useEventListener'
import { useGlobalShortcuts } from '@/composables/useGlobalShortcuts'
import { useShellShortcuts } from '@/composables/useShellShortcuts'
import { useBoardDetailShortcuts } from '@/composables/useKeyboardShortcuts'

const push = vi.hoisted(() => vi.fn(async () => {}))
vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRouter: () => ({ push }),
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))

function mountPage() {
  const open = ref(false)
  const logout = vi.fn(async () => {})
  const toggleTheme = vi.fn()
  const setActiveDropdown = vi.fn()
  const focusSearch = vi.fn()
  const goToNextPage = vi.fn()
  const goToWrite = vi.fn()
  const toggleSubscribe = vi.fn()
  const submit = vi.fn()
  const wrapper = mount(defineComponent({
    setup() {
      const { handleKeyDown } = useShellShortcuts({
        authStore: { isAuthenticated: true, logout },
        keyboardStore: { isShortcutsModalOpen: false, closeShortcutsModal: vi.fn(), toggleShortcutsModal: vi.fn() },
        router: { push },
        isMobile: ref(false),
        hasOpenDropdown: () => false,
        closeAllDropdowns: vi.fn(),
        setActiveDropdown,
        toggleTheme,
      })
      useEventListener(() => document, 'keydown', handleKeyDown)
      useGlobalShortcuts().registerShortcut({ key: '/', handler: focusSearch })
      useBoardDetailShortcuts({
        goToNextPage, goToPrevPage: vi.fn(), goToFirstPage: vi.fn(), goToLastPage: vi.fn(),
        goToWrite, toggleSubscribe, canWrite: true, canGoNext: true, canGoPrev: true,
      })
      return () => h('div', [
        h('button', { onClick: () => { open.value = true } }, 'Open'),
        h(BaseModal, { isOpen: open.value, title: 'Edit', onClose: () => { open.value = false } }, {
          default: () => h('input', { onKeydown: (event: KeyboardEvent) => {
            if (event.key === 'Enter') submit()
          } }),
        }),
      ])
    },
  }), { attachTo: document.body })
  return { wrapper, open, logout, toggleTheme, setActiveDropdown, focusSearch, goToNextPage, goToWrite, toggleSubscribe, submit }
}

function key(value: string, options: KeyboardEventInit = {}) {
  const event = new KeyboardEvent('keydown', { key: value, bubbles: true, cancelable: true, ...options })
  ;(document.activeElement ?? document).dispatchEvent(event)
  return event
}

afterEach(() => {
  vi.clearAllMocks()
  document.body.replaceChildren()
})

describe('modal global shortcut isolation', () => {
  it('blocks shell, search and board shortcuts on modal buttons, then restores them after close', async () => {
    const state = mountPage()
    state.open.value = true
    await nextTick()
    await nextTick()
    expect(document.activeElement?.tagName).toBe('BUTTON')
    expect(document.activeElement?.closest('[aria-modal="true"]')).not.toBeNull()

    for (const value of ['q', 'h', 'd', 'b', '/', 'n', ']', 'f', 'g', 'm']) key(value)
    key('k', { ctrlKey: true })
    await flushPromises()
    for (const action of [state.logout, push, state.toggleTheme, state.setActiveDropdown, state.focusSearch,
      state.goToNextPage, state.goToWrite, state.toggleSubscribe]) expect(action).not.toHaveBeenCalled()

    const input = document.querySelector('input')!
    input.focus()
    key('Enter')
    expect(state.submit).toHaveBeenCalledOnce()
    key('Escape')
    await nextTick()
    await nextTick()
    expect(state.open.value).toBe(false)
    state.wrapper.get('button').element.focus()
    key('q')
    await flushPromises()
    expect(state.logout).toHaveBeenCalledOnce()
    expect(push).toHaveBeenCalledWith('/')
    state.wrapper.unmount()
  })

  it.each([['b', '/boards'], ['m', '/mypage']])('preserves g then %s navigation after the shell handler', async (keyName, destination) => {
    const state = mountPage()
    state.wrapper.get('button').element.focus()
    key('g')
    key(keyName!)
    await flushPromises()
    expect(push).toHaveBeenCalledWith(destination)
    state.wrapper.unmount()
  })

  it('discards a pending navigation prefix when a modal consumes the next key', async () => {
    const state = mountPage()
    state.wrapper.get('button').element.focus()
    key('g')
    state.open.value = true
    await nextTick()
    await nextTick()
    key('h')
    expect(push).not.toHaveBeenCalled()
    state.open.value = false
    await nextTick()
    await nextTick()
    state.wrapper.get('button').element.focus()
    key('m')
    expect(push).not.toHaveBeenCalled()
    state.wrapper.unmount()
  })

  it('respects keys already handled by a local control', async () => {
    const state = mountPage()
    const button = state.wrapper.get('button').element
    button.focus()
    button.addEventListener('keydown', (event) => event.preventDefault())
    key('q')
    key('/')
    key(']')
    await flushPromises()
    expect(state.logout).not.toHaveBeenCalled()
    expect(state.focusSearch).not.toHaveBeenCalled()
    expect(state.goToNextPage).not.toHaveBeenCalled()
    state.wrapper.unmount()
  })
})
