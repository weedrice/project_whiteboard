import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, nextTick, ref, toRef } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { useDialogLifecycle } from '@/composables/useDialogLifecycle'

const mountedWrappers: VueWrapper[] = []

const DialogHarness = defineComponent({
  props: {
    open: { type: Boolean, required: true },
  },
  emits: ['close'],
  setup(props, { emit }) {
    const overlayRef = ref<HTMLElement | null>(null)
    const dialogRef = ref<HTMLElement | null>(null)
    const { isTopDialog } = useDialogLifecycle({
      isOpen: toRef(props, 'open'),
      overlayRef,
      dialogRef,
      close: () => emit('close'),
    })

    return { dialogRef, isTopDialog, overlayRef }
  },
  template: `
    <div
      v-if="open"
      ref="overlayRef"
      role="dialog"
      aria-label="Lifecycle harness"
      :aria-modal="isTopDialog ? 'true' : undefined"
      :aria-hidden="isTopDialog ? undefined : 'true'"
      :inert="isTopDialog ? undefined : true"
    >
      <section ref="dialogRef">
        <button type="button">First action</button>
        <button type="button">Last action</button>
      </section>
    </div>
  `,
})

afterEach(() => {
  mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  document.body.style.overflow = ''
  document.body.replaceChildren()
})

describe('useDialogLifecycle', () => {
  it('shares modal scroll, focus, Escape, and focus-return behavior', async () => {
    let escapedToPage = false
    const pageEscapeHandler = (event: KeyboardEvent) => {
      if (event.key === 'Escape') escapedToPage = true
    }
    document.body.addEventListener('keydown', pageEscapeHandler)
    const trigger = document.createElement('button')
    trigger.textContent = 'Open dialog'
    document.body.appendChild(trigger)
    trigger.focus()

    const host = document.createElement('div')
    document.body.appendChild(host)
    const wrapper = mount(DialogHarness, {
      attachTo: host,
      props: { open: false },
    })
    mountedWrappers.push(wrapper)

    await wrapper.setProps({ open: true })
    await nextTick()
    await nextTick()

    expect(document.body.style.overflow).toBe('hidden')
    expect(wrapper.get('[role="dialog"]').attributes('aria-modal')).toBe('true')
    expect(trigger.hasAttribute('inert')).toBe(true)
    expect(document.activeElement?.textContent).toBe('First action')

    document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(escapedToPage).toBe(false)

    await wrapper.setProps({ open: false })
    await nextTick()
    await nextTick()

    expect(document.body.style.overflow).toBe('')
    expect(trigger.hasAttribute('inert')).toBe(false)
    expect(document.activeElement).toBe(trigger)
    document.body.removeEventListener('keydown', pageEscapeHandler)
  })

  it('wraps Tab focus inside the active custom dialog', async () => {
    const wrapper = mount(DialogHarness, {
      attachTo: document.body,
      props: { open: true },
    })
    mountedWrappers.push(wrapper)
    await nextTick()
    await nextTick()

    const buttons = wrapper.findAll('button')
    buttons[1].element.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    expect(document.activeElement).toBe(buttons[0].element)

    buttons[0].element.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }))
    expect(document.activeElement).toBe(buttons[1].element)
  })

  it('exposes only the top custom dialog and returns focus to its parent', async () => {
    const parent = mount(DialogHarness, {
      attachTo: document.body,
      props: { open: true },
    })
    const child = mount(DialogHarness, {
      attachTo: document.body,
      props: { open: false },
    })
    mountedWrappers.push(parent, child)
    await nextTick()
    await nextTick()

    const parentReturnTarget = parent.findAll('button')[1].element
    parentReturnTarget.focus()
    await child.setProps({ open: true })
    await nextTick()
    await nextTick()

    expect(parent.get('[role="dialog"]').attributes('aria-modal')).toBeUndefined()
    expect(parent.get('[role="dialog"]').attributes('aria-hidden')).toBe('true')
    expect(parent.get('[role="dialog"]').attributes('inert')).toBeDefined()
    expect(child.get('[role="dialog"]').attributes('aria-modal')).toBe('true')

    document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(child.emitted('close')).toHaveLength(1)
    expect(parent.emitted('close')).toBeUndefined()

    await child.setProps({ open: false })
    await nextTick()
    await nextTick()

    expect(parent.get('[role="dialog"]').attributes('aria-modal')).toBe('true')
    expect(document.activeElement).toBe(parentReturnTarget)
  })
})
