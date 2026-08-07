import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PostPreviewModal from '../PostPreviewModal.vue'

describe('PostPreviewModal', () => {
  it('uses a viewport-sized scroll container and an eager sandbox preview', () => {
    const wrapper = mount(PostPreviewModal, {
      props: {
        isOpen: true,
        postTitle: 'Long preview',
        tags: [],
        content: '<style>body{min-height:2000px}</style><p>Long</p>',
      },
      global: {
        stubs: {
          Teleport: true,
        },
      },
    })

    expect(wrapper.get('.modal-container').classes()).toContain('max-w-[90vw]')
    expect(wrapper.get('.modal-container').classes()).toContain('modal-container-mobile-full')
    expect(wrapper.get('.modal-body').classes()).toContain('overflow-y-auto')
    expect(wrapper.get('.modal-body').classes()).toContain('sm:max-h-[calc(90dvh-10rem)]')
    expect(wrapper.get('iframe').attributes('style')).toContain('height: 420px')
    expect(wrapper.get('iframe').attributes('loading')).toBe('eager')
  })
})
