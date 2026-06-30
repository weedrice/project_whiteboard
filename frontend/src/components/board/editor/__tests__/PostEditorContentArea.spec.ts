import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PostEditorContentArea from '@/components/board/editor/PostEditorContentArea.vue'

vi.mock('@tiptap/vue-3', () => ({
  EditorContent: {
    props: ['editor'],
    template: '<div data-testid="editor-content" />',
  },
}))

describe('PostEditorContentArea', () => {
  it('renders drop overlay and forwards content events', async () => {
    const wrapper = mount(PostEditorContentArea, {
      props: {
        editor: undefined,
        isDraggingImage: true,
        dropImageHint: 'Drop image here',
      },
    })

    await wrapper.get('.tiptap-content').trigger('mousedown')
    await wrapper.get('.tiptap-content').trigger('paste')
    await wrapper.get('.tiptap-content').trigger('drop')
    await wrapper.get('.tiptap-content').trigger('dragenter')
    await wrapper.get('.tiptap-content').trigger('dragleave')

    expect(wrapper.find('[data-testid="editor-content"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Drop image here')
    expect(wrapper.emitted('content-mousedown')).toHaveLength(1)
    expect(wrapper.emitted('content-paste')).toHaveLength(1)
    expect(wrapper.emitted('content-drop')).toHaveLength(1)
    expect(wrapper.emitted('content-dragenter')).toHaveLength(1)
    expect(wrapper.emitted('content-dragleave')).toHaveLength(1)
  })
})
