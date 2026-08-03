import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import PostDraftStatusPanel from '@/components/board/PostDraftStatusPanel.vue'

const BaseButtonStub = defineComponent({
  props: {
    to: { type: String, default: '' },
  },
  setup(props, { slots }) {
    return () => h('a', { href: props.to }, slots.default?.())
  },
})

describe('PostDraftStatusPanel', () => {
  it('links to the draft list when automatic recovery is ambiguous', () => {
    const wrapper = mount(PostDraftStatusPanel, {
      props: {
        label: 'board.writePost.draftStatus.multipleFound',
        draftEnabled: true,
        isSavingDraft: false,
        isRestoringDraft: false,
        draftConflict: false,
        draftProtected: false,
        draftDeleted: false,
        restoreFailed: false,
        multipleDraftsFound: true,
        saveFailed: false,
      },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { BaseButton: BaseButtonStub },
      },
    })

    expect(wrapper.text()).toContain('board.writePost.draftStatus.openDrafts')
    expect(wrapper.get('a').attributes('href')).toBe('/mypage/drafts')
  })
})
