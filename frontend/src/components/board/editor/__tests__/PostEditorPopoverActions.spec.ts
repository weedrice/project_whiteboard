import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PostEditorPopoverActions from '../PostEditorPopoverActions.vue'

describe('PostEditorPopoverActions', () => {
  it('renders cancel, secondary, and apply actions in order', async () => {
    const wrapper = mount(PostEditorPopoverActions, {
      props: {
        cancelLabel: 'Cancel',
        applyLabel: 'Apply',
      },
      slots: {
        'secondary-action': '<button type="button" class="link-popover-remove">Remove</button>',
      },
    })

    const buttons = wrapper.findAll('.link-popover-actions button')

    expect(buttons.map((button) => button.text())).toEqual(['Cancel', 'Remove', 'Apply'])

    await buttons[0].trigger('click')
    await buttons[2].trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('apply')).toHaveLength(1)
  })
})
