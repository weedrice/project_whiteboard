import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MentionSuggestionList from '../MentionSuggestionList.vue'

describe('MentionSuggestionList', () => {
  it('connects listbox options and supports native button activation', async () => {
    const candidate = { userId: 7, displayName: 'Alice', profileImageUrl: null }
    const wrapper = mount(MentionSuggestionList, {
      props: {
        id: 'comment-mention-list',
        items: [candidate],
        selectedIndex: 0,
      },
    })

    expect(wrapper.get('[role="listbox"]').attributes('id')).toBe('comment-mention-list')
    const option = wrapper.get('[role="option"]')
    expect(option.attributes()).toMatchObject({
      id: 'comment-mention-list-option-7',
      'aria-selected': 'true',
      type: 'button',
    })

    await option.trigger('click')
    expect(wrapper.emitted('select')).toEqual([[candidate]])
  })

  it('announces lookup failure and exposes a retry action', async () => {
    const wrapper = mount(MentionSuggestionList, {
      props: {
        items: [],
        selectedIndex: 0,
        error: true,
      },
      global: {
        mocks: { $t: (key: string) => key },
      },
    })

    expect(wrapper.get('[role="alert"]').attributes('aria-live')).toBe('assertive')
    await wrapper.get('.mention-suggestion-retry').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
