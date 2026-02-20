import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import PostEdit from '../PostEdit.vue'

describe('PostEdit', () => {
  it('renders PostForm with mode edit', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/board/test/post/1/edit',
          component: PostEdit
        }
      ]
    })

    await router.push('/board/test/post/1/edit')
    await router.isReady()

    const wrapper = mount(
      { template: '<router-view />' },
      {
        global: {
          plugins: [router],
          stubs: {
            PostForm: {
              name: 'PostForm',
              template: '<div data-testid="post-form" :data-mode="mode"></div>',
              props: ['mode']
            }
          }
        }
      }
    )

    await wrapper.vm.$nextTick()
    const form = wrapper.find('[data-testid="post-form"]')
    expect(form.exists()).toBe(true)
    expect(form.attributes('data-mode')).toBe('edit')
  })
})
