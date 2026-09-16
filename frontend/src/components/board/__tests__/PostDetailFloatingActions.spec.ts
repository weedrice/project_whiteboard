import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PostDetailQuickActions from '../PostDetailQuickActions.vue'

describe('post detail floating actions', () => {
  it('keeps only the bottom quick actions', async () => {
    const quickActions = mount(PostDetailQuickActions, {
      props: { visible: true },
      global: { mocks: { $t: (key: string) => key } },
    })

    expect(quickActions.get('.nv-post-mobile-actions').classes()).not.toContain('xl:hidden')
    expect(quickActions.find('.nv-post-board-actions').exists()).toBe(false)
    const actions = quickActions.findAll('.nv-post-mobile-action')
    expect(actions).toHaveLength(3)

    await actions[0]?.trigger('click')
    expect(quickActions.emitted('comments')).toHaveLength(1)
  })
})
