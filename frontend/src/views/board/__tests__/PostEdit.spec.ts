import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PostEdit from '../PostEdit.vue'

describe('PostEdit', () => {
  it('renders PostForm with mode edit', () => {
    const wrapper = mount(PostEdit, {
      global: {
        stubs: {
          PostForm: {
            name: 'PostForm',
            template: '<div data-testid="post-form" :data-mode="mode"></div>',
            props: ['mode']
          }
        }
      }
    })
    const form = wrapper.find('[data-testid="post-form"]')
    expect(form.exists()).toBe(true)
    expect(form.attributes('data-mode')).toBe('edit')
  })
})
