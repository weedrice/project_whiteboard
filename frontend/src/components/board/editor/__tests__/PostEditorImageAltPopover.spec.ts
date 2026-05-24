import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostEditorImageAltPopover from '../PostEditorImageAltPopover.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

const baseButtonStub = {
  props: ['type'],
  emits: ['click'],
  template: '<button :type="type || \'button\'" @click="$emit(\'click\', $event)"><slot /></button>',
}

const mountPopover = () => mount(PostEditorImageAltPopover, {
  props: {
    alt: 'Original alt',
  },
  global: {
    stubs: {
      BaseButton: baseButtonStub,
    },
  },
})

describe('PostEditorImageAltPopover', () => {
  it('connects the image alt label and help text to the input', () => {
    const wrapper = mountPopover()

    expect(wrapper.get('label[for="editor-image-alt"]').text()).toBe('board.writePost.imageAlt.label')
    expect(wrapper.get('#editor-image-alt').attributes()).toMatchObject({
      name: 'editorImageAlt',
      autocomplete: 'off',
      'aria-describedby': 'editor-image-alt-help',
    })
    expect(wrapper.get('#editor-image-alt-help').text()).toBe('board.writePost.imageAlt.help')
  })

  it('emits apply, clear, and close actions', async () => {
    const wrapper = mountPopover()

    await wrapper.get('#editor-image-alt').setValue('Updated alt')
    await wrapper.get('#editor-image-alt').trigger('keydown.enter')
    await wrapper.get('#editor-image-alt').trigger('keydown.escape')
    await wrapper.get('.link-popover-remove').trigger('click')

    expect(wrapper.emitted('apply')).toEqual([['Updated alt']])
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('clear')).toHaveLength(1)
  })
})
