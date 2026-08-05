import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import PostDraftStatusPanel from '@/components/board/PostDraftStatusPanel.vue'

const BaseButtonStub = defineComponent({
  props: {
    to: { type: String, default: '' },
  },
  setup(props, { attrs, slots }) {
    return () => h(props.to ? 'a' : 'button', { ...attrs, href: props.to || undefined }, slots.default?.())
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
        protectedDraftForkAvailable: false,
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

  it('offers save-as-new and discard actions for unsaved edits detached from a protected draft', async () => {
    const wrapper = mount(PostDraftStatusPanel, {
      props: {
        label: 'board.writePost.draftStatus.protected',
        draftEnabled: true,
        isSavingDraft: false,
        isRestoringDraft: false,
        draftConflict: false,
        draftProtected: true,
        protectedDraftForkAvailable: true,
        draftDeleted: false,
        restoreFailed: false,
        multipleDraftsFound: false,
        saveFailed: false,
      },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { BaseButton: BaseButtonStub },
      },
    })

    const buttons = wrapper.findAll('button')
    await buttons[0]?.trigger('click')
    await buttons[1]?.trigger('click')

    expect(wrapper.emitted('saveProtectedAsNew')).toHaveLength(1)
    expect(wrapper.emitted('discardProtected')).toHaveLength(1)
    expect(wrapper.text()).toContain('board.writePost.draftStatus.openScheduledPosts')
  })
})
