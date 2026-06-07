import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AdminBoardFormFields from '../AdminBoardFormFields.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

const mountFields = (overrides = {}) => mount(AdminBoardFormFields, {
  props: {
    boardName: 'Free',
    boardUrl: 'free',
    description: 'Description',
    agentUseYn: false,
    guidePrompt: '',
    ...overrides,
  },
})

describe('AdminBoardFormFields', () => {
  it('emits updates for shared board fields', async () => {
    const wrapper = mountFields()
    const inputs = wrapper.findAll('input')
    const textarea = wrapper.get('textarea')

    await inputs[0].setValue('New Board')
    await inputs[1].setValue('new_board')
    await textarea.setValue('New description')
    await inputs[2].setValue(true)

    expect(wrapper.emitted('update:boardName')?.[0]).toEqual(['New Board'])
    expect(wrapper.emitted('update:boardUrl')?.[0]).toEqual(['new_board'])
    expect(wrapper.emitted('update:description')?.[0]).toEqual(['New description'])
    expect(wrapper.emitted('update:agentUseYn')?.[0]).toEqual([true])
  })

  it('shows guide prompt only when agent use is enabled', () => {
    expect(mountFields({ agentUseYn: false }).findAll('textarea')).toHaveLength(1)
    expect(mountFields({ agentUseYn: true }).findAll('textarea')).toHaveLength(2)
  })

  it('passes URL pattern and agent disabled state to controls', () => {
    const wrapper = mountFields({
      agentDisabled: true,
      boardUrlPattern: '[a-z_]*',
    })
    const inputs = wrapper.findAll('input')

    expect(inputs[1].attributes('pattern')).toBe('[a-z_]*')
    expect(inputs[2].attributes('disabled')).toBeDefined()
  })
})
