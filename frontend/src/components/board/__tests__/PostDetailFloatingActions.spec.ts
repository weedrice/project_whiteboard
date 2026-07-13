import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PostDetailComposerCta from '../PostDetailComposerCta.vue'
import PostDetailQuickActions from '../PostDetailQuickActions.vue'

describe('post detail floating actions', () => {
  it('keeps only the bottom quick actions alongside the composer CTA', () => {
    const quickActions = mount(PostDetailQuickActions, {
      props: { visible: true },
      global: { mocks: { $t: (key: string) => key } },
    })
    const composerCta = mount(PostDetailComposerCta, {
      props: { visible: true },
      global: { mocks: { $t: (key: string) => key } },
    })

    expect(quickActions.get('.nv-post-mobile-actions').classes()).not.toContain('xl:hidden')
    expect(quickActions.find('.nv-post-board-actions').exists()).toBe(false)
    expect(composerCta.get('.nv-post-mobile-comment-cta').classes()).toContain('sm:hidden')
  })
})
