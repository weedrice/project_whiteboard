import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PostPollEditor from '@/components/board/PostPollEditor.vue'

const existingPoll = {
  question: 'Pick one',
  options: ['Alpha', 'Beta'],
  multipleChoiceEnabled: false,
  anonymousEnabled: false,
  closesAt: null,
}

describe('PostPollEditor', () => {
  it('makes an existing poll explicitly read-only while editing a post', async () => {
    const wrapper = mount(PostPollEditor, {
      props: {
        modelValue: existingPoll,
        readOnly: true,
      },
      global: {
        mocks: { $t: (key: string) => key },
      },
    })

    expect(wrapper.get('[role="status"]').text()).toBe('board.writePost.poll.readOnlyNotice')
    expect(wrapper.get('[data-testid="poll-question"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="poll-option-0"]').attributes('disabled')).toBeDefined()

    const removeButton = wrapper.findAll('button')
      .find((button) => button.text() === 'board.writePost.poll.remove')
    expect(removeButton?.attributes('disabled')).toBeDefined()
    await removeButton?.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('keeps poll controls editable when read-only mode is not requested', async () => {
    const wrapper = mount(PostPollEditor, {
      props: { modelValue: existingPoll },
      global: {
        mocks: { $t: (key: string) => key },
      },
    })

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="poll-question"]').attributes('disabled')).toBeUndefined()

    const removeButton = wrapper.findAll('button')
      .find((button) => button.text() === 'board.writePost.poll.remove')
    expect(removeButton?.attributes('disabled')).toBeUndefined()
    await removeButton?.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[null]])
  })
})
