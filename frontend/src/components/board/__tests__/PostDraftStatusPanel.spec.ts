import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import PostDraftStatusPanel from '@/components/board/PostDraftStatusPanel.vue'
import type { DraftPresentation } from '@/features/board/posts/draft/postDraftContracts'

const BaseButtonStub = defineComponent({
  props: {
    to: { type: String, default: '' },
  },
  setup(props, { attrs, slots }) {
    return () => h(props.to ? 'a' : 'button', { ...attrs, href: props.to || undefined }, slots.default?.())
  },
})

describe('PostDraftStatusPanel', () => {
  it('renders presentation actions in order and emits their unified action ids', async () => {
    const presentation: DraftPresentation = {
      label: 'protected',
      busy: false,
      actions: [
        { id: 'save-as-new', label: 'save as new', variant: 'primary', disabled: false },
        { id: 'discard-local', label: 'discard local', variant: 'secondary', disabled: false },
        {
          id: 'open-scheduled',
          label: 'open scheduled',
          variant: 'secondary',
          disabled: false,
          to: '/mypage/drafts',
        },
      ],
    }
    const wrapper = mount(PostDraftStatusPanel, {
      props: { presentation },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { BaseButton: BaseButtonStub },
      },
    })

    expect(wrapper.text()).toContain('protected')
    expect(wrapper.findAll('button').map((button) => button.text())).toEqual(['save as new', 'discard local'])
    expect(wrapper.get('a').attributes('href')).toBe('/mypage/drafts')

    await wrapper.findAll('button')[0]?.trigger('click')
    await wrapper.findAll('button')[1]?.trigger('click')
    await wrapper.get('a').trigger('click')

    expect(wrapper.emitted('action')).toEqual([
      ['save-as-new'],
      ['discard-local'],
      ['open-scheduled'],
    ])
  })
})
