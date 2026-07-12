import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import type { Editor } from '@tiptap/core'
import PostEditorToolbar from '../PostEditorToolbar.vue'

describe('PostEditorToolbar responsive contract', () => {
  it('keeps both toolbar rows in independent horizontal scroll containers', () => {
    const wrapper = mount(PostEditorToolbar, {
      props: {
        editor: { isActive: vi.fn(() => false) } as unknown as Editor,
        isUploadingImage: false,
        hasImageUploadError: false,
        currentUploadingImageName: '',
        imageUploadQueueCount: 0,
        failedImageCount: 0,
        failedImageFiles: [],
        fontSizes: ['14px'],
        lineHeights: ['1.5'],
        codeBlockLanguages: ['javascript'],
        currentFontSize: '',
        currentLineHeight: '',
        currentCodeBlockLanguage: '',
        currentTextColor: '',
        isDefaultColor: true,
        isDark: false,
        showSlashMenu: false,
        showTablePopover: false,
        showColorPanel: false,
        activeTextAlign: '',
      },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { PostEditorImageUploadStatus: true },
      },
    })

    expect(wrapper.findAll('.tiptap-toolbar-row--scrollable')).toHaveLength(2)
    expect(wrapper.find('.tiptap-toolbar-group--format').exists()).toBe(true)
  })
})
