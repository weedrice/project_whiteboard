import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ErrorState from '../ErrorState.vue'

describe('ErrorState', () => {
  it('moves focus to a newly rendered blocking error when requested', async () => {
    const wrapper = mount(ErrorState, {
      props: {
        message: 'Load failed',
        autoFocus: true,
      },
      attachTo: document.body,
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          BaseButton: { template: '<button><slot /></button>' },
          AlertCircle: true,
        },
      },
    })

    await flushPromises()

    expect(wrapper.get('[role="alert"]').attributes('tabindex')).toBe('-1')
    expect(document.activeElement).toBe(wrapper.get('[role="alert"]').element)
    wrapper.unmount()
  })
})
