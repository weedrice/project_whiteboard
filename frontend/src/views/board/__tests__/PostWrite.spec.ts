import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import PostWrite from '../PostWrite.vue'

describe('PostWrite', () => {
  it('renders PostForm with mode create', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/board/test/write',
          component: PostWrite
        }
      ]
    })

    await router.push('/board/test/write')
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
    expect(form.attributes('data-mode')).toBe('create')
  })
})
