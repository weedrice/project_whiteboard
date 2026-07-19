import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useUnsavedChangesGuard } from '../useUnsavedChangesGuard'

const routeGuard = vi.hoisted(() => ({
  callback: undefined as undefined | (() => boolean | Promise<boolean>),
}))

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: vi.fn((callback) => {
    routeGuard.callback = callback
  }),
}))

describe('useUnsavedChangesGuard', () => {
  beforeEach(() => {
    routeGuard.callback = undefined
  })

  function mountGuard(dirty = false, pending = false, confirm = vi.fn().mockResolvedValue(true)) {
    const hasUnsavedChanges = ref(dirty)
    const isPending = ref(pending)
    const wrapper = mount(defineComponent({
      setup() {
        useUnsavedChangesGuard(hasUnsavedChanges, isPending, () => 'leave?', confirm)
        return {}
      },
      template: '<div />',
    }))
    return { wrapper, hasUnsavedChanges, isPending, confirm }
  }

  it('asks before leaving a dirty page', async () => {
    const { wrapper, confirm } = mountGuard(true)
    expect(await routeGuard.callback?.()).toBe(true)
    expect(confirm).toHaveBeenCalledWith('leave?')
    wrapper.unmount()
  })

  it('blocks route and browser unload without prompting during a save', async () => {
    const { wrapper, confirm } = mountGuard(false, true)
    expect(await routeGuard.callback?.()).toBe(false)
    expect(confirm).not.toHaveBeenCalled()

    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    wrapper.unmount()
  })

  it('removes the browser unload listener on unmount', () => {
    const { wrapper } = mountGuard(true)
    wrapper.unmount()
    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)
  })
})
