import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
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

  it('keeps the editor toolbar controls compact', () => {
    const toolbarStyles = readFileSync(resolve(process.cwd(), 'src/components/board/editor/editor-toolbar.css'), 'utf8')

    expect(toolbarStyles).toMatch(/\.tiptap-btn\s*{[\s\S]*?width: 2\.25rem;[\s\S]*?height: 2\.25rem;/)
    expect(toolbarStyles).toMatch(/\.tiptap-select\s*{[\s\S]*?height: 2\.25rem;/)
  })
})
