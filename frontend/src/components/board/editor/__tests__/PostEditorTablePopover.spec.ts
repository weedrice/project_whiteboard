import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostEditorTablePopover from '../PostEditorTablePopover.vue'

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

const mountPopover = () => mount(PostEditorTablePopover, {
  props: {
    rows: 3,
    cols: 4,
    headerRow: true,
  },
  global: {
    stubs: {
      BaseButton: baseButtonStub,
    },
  },
})

describe('PostEditorTablePopover', () => {
  it('connects table option labels to named controls', () => {
    const wrapper = mountPopover()

    expect(wrapper.get('label[for="editor-table-rows"]').text()).toBe('board.writePost.tableRows')
    expect(wrapper.get('#editor-table-rows').attributes()).toMatchObject({
      name: 'tableRows',
      inputmode: 'numeric',
      autocomplete: 'off',
    })
    expect(wrapper.get('label[for="editor-table-cols"]').text()).toBe('board.writePost.tableCols')
    expect(wrapper.get('#editor-table-cols').attributes()).toMatchObject({
      name: 'tableCols',
      inputmode: 'numeric',
      autocomplete: 'off',
    })
    expect(wrapper.get('label[for="table-header-row"]').text()).toBe('board.writePost.tableHeaderRow')
    expect(wrapper.get('#table-header-row').attributes('name')).toBe('tableHeaderRow')
  })

  it('emits updates and action events from controls', async () => {
    const wrapper = mountPopover()

    await wrapper.get('#editor-table-rows').setValue('5')
    await wrapper.get('#editor-table-cols').setValue('6')
    await wrapper.get('#table-header-row').setValue(false)
    await wrapper.get('#editor-table-rows').trigger('keydown.enter')
    await wrapper.get('#editor-table-cols').trigger('keydown.escape')

    expect(wrapper.emitted('update:rows')?.[0]).toEqual([5])
    expect(wrapper.emitted('update:cols')?.[0]).toEqual([6])
    expect(wrapper.emitted('update:header-row')?.[0]).toEqual([false])
    expect(wrapper.emitted('apply')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
